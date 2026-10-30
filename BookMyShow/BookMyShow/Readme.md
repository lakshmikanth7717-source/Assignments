# BookMyShow booking engine

A Java 17 / Spring Boot 3.2.5 service for show-specific seat holds and authenticated, idempotent payment events. MySQL is the source of truth; Redis coordinates short booking operations. The project provides service methods, a Flyway schema, executable sample data and tests. HTTP controllers and a real payment provider are outside this service-only implementation.

## Project layout

The Maven project is rooted at `src/pom.xml`. Run commands below from this directory.

| Location | Purpose |
| --- | --- |
| src/main/java/entity | Physical layout, show inventory, bookings, allocation history and payments |
| src/main/java/repo | JPA repositories and conditional inventory updates |
| service/BookingService | Redis coordination and stable caller-facing responses |
| service/BookingTransactionService | Database transactions and business rules |
| service/BookingExpiryService | Scheduled expiry sweep |
| src/main/resources/db/migration | Authoritative Flyway schema |
| src/main/resources/sql | Executable sample rows and show-listing query |
| src/test/java/service/BookingScenarios.java | Shared persistence and concurrency tests |
| queries.md | P2 query assumption and parameters |
| REVIEW_FIXES.md | Resolution of all 21 bot comments |

## Build and verification

Install Maven and a JDK supporting Java 17. No Maven wrapper is included.

```shell
mvn -f src/pom.xml clean verify
```

The default suite runs the real migration, Hibernate schema validation, actual JPA repositories, JDBC transactions and concurrent requests on H2 in MySQL mode. Its Redis replacement is an explicitly named in-process lock double; it does not claim to test MySQL or live Redis behavior.

Run the same scenarios against real MySQL 8 and Redis with Docker installed and running:

```shell
mvn -f src/pom.xml -Preal-integration verify
```

This explicit profile fails if Docker is unavailable instead of silently skipping. The GitHub Actions workflow runs this profile on a Docker-capable runner.

For a larger contention run, set between 2 and 1000 requests:

```shell
mvn -f src/pom.xml -Preal-integration -Dbooking.test.clients=200 verify
```

The test measures elapsed time and requires exactly one winner for a shared seat. It makes no hardware-independent throughput claim. Tests also cover database guards without Redis, separate shows, atomic multi-seat rollback, expiry/reassignment, callback races, failed/late payments, signatures, amount validation, database constraints, cancellation and transaction propagation.

## Run locally

Provide MySQL and Redis, or use the development Compose file. For example, in PowerShell:

```powershell
$env:DB_PASSWORD = "<your-local-database-password>"
$env:DB_ROOT_PASSWORD = "<a-different-local-root-password>"
$env:BOOKING_WEBHOOK_SECRET = "<a-random-shared-signing-secret>"

docker compose up -d --wait
mvn -f src/pom.xml spring-boot:run
```

Optional variables: `DB_URL`, `DB_USER`, `REDIS_HOST`, `REDIS_PORT`, `BOOKING_EXPIRY_INTERVAL_MS`. Defaults are database `booking_engine` and user `booking`. All application timestamps use UTC. Compose can read a local `.env`; Java still needs its environment variables exported. Never commit real credentials.

Flyway creates a fresh schema and Hibernate validates it. The prototype SQL was not an applied Flyway migration and did not match its entities. Use a new development database for this baseline. Do not baseline an incompatible database or delete existing data to force startup; existing installations need a separately reviewed data migration.

The packaged JAR is `src/target/booking-engine-0.0.1-SNAPSHOT.jar`. The entry point is `main.BookMyShowApplication`. Import `src/pom.xml` in IntelliJ, with `src/main/java` and `src/test/java` as the source roots.

## Seat holds

A show ID is required because the same physical seat is sold independently for different shows.

```java
BookingResult single = bookingService.holdSeatForFiveMinutes(101L, 1001L, 42L);
BookingResult group = bookingService.holdSeatsForFiveMinutes(
        101L, List.of(1001L, 1002L), 42L);
```

A successful result includes the booking ID, expiry, total and currency. A hold lasts up to five minutes, ending earlier if the show starts.

Locks are acquired in ascending seat order using `lock:show:<showId>:seat:<seatId>`. Redisson's watchdog covers the short database operation. The database creates a pending booking, conditionally claims every show-seat and records priced booking items in one transaction. Any failed claim rolls everything back.

Redis is unlocked after the call. The persistent show-seat owner and deadline represent the reservation; Redis is not held for five minutes. Conditional database updates remain the correctness guard if Redis coordination is lost.

Physical seats are unique by screen/row/number. Current inventory is unique by show/seat. Historical items are unique only within their booking, so expiry/cancellation does not permanently prevent rebooking.

## Expiry and cancellation

Expired HOLD rows can be reclaimed immediately, without waiting for the sweep. The sweep runs every 30 seconds by default, locks each expired pending booking, and releases only inventory still owned by that booking. It cannot release a replacement reservation, and repeated sweeps are harmless.

```java
BookingResult cancelled = bookingService.cancelBooking(bookingId, userId);
```

Only the booking owner can cancel a pending hold. Confirmed bookings require a separate refund/cancellation workflow.

## Payment event contract

```java
WebhookResult result =
        bookingService.processPaymentWebhookIdempotently(rawJson, signature);
```

Example payload, using a booking ID returned by a hold:

```json
{
  "idempotencyKey": "evt-123",
  "bookingId": "the-booking-id",
  "gatewayTransactionId": "txn-456",
  "amount": 200.00,
  "currency": "INR",
  "outcome": "SUCCESS"
}
```

The hexadecimal HMAC-SHA256 signature covers the exact UTF-8 JSON bytes, including whitespace, using `BOOKING_WEBHOOK_SECRET`. Missing configuration fails closed. Verification precedes parsing and database writes. This is a provider-neutral contract: a real adapter must implement that gateway's signing/event protocol. Do not assume compatibility with Stripe, Razorpay or another provider.

Amounts use BigDecimal end-to-end and reject null, negative/out-of-range values and fractional cents. Amount and currency must match the booking. Prices currently use INR.

A booking-row lock serializes its callbacks. Unique event and gateway-transaction constraints prevent cross-booking reuse. A repeated terminal event must match the recorded booking, transaction, amount, currency and outcome. Repeats do not apply changes again. Reference conflicts are rejected after the transactional call has rolled back.

Only SUCCESS can confirm a pending, unexpired booking for an active future show. The exact allocation, user and deadline must match; the conditional confirmation count must equal the expected seat count.

FAILED is recorded without confirming seats and releases the pending booking's own holds. Outcomes are terminal per transaction ID; a new attempt requires a new gateway transaction ID. Contradictory terminal events are rejected.

A valid late SUCCESS, a cancelled booking/show, a lost allocation or an extra successful payment is recorded as REFUND_REQUIRED without taking another user's seats. An external worker/operator must fulfill that obligation; the project does not issue a real refund.

`WebhookResult.success()` means the event was processed, including a decline or refund obligation. Use `paymentApplied()` and `refundRequired()` to distinguish outcomes. Unexpected exception details are logged internally and replaced with stable caller-facing messages.

## Transaction boundary

Database methods live in a separate Spring bean and use plain `@Transactional` with default REQUIRED propagation. Without a caller transaction, commit finishes before Redis unlock and webhook error handling.

An existing caller transaction is joined. Its rollback undoes booking/payment changes; an inner failure can mark it rollback-only; commit can occur after Redis unlock. Keep normal coordinator entry points outside an outer transaction when commit-before-unlock is required. Separate beans alone do not imply independent transactions.

## Schema and submission material

The schema covers venues, screens, movies, shows, seats, show-seat inventory, bookings, booking items and payments. Foreign keys and checks enforce references, allowed states, valid ownership shapes and non-negative amounts.

Optional [sample data](src/main/resources/sql/sample-data.sql) contains executable examples for every table. Import it once into a fresh development schema after migration; it is not loaded automatically in production.

[queries.md](queries.md) explains the executable show-listing query and its assumed P2 scope. The original assignment brief was unavailable, so verify the exact filter/output requirements before submission. [entities.md](entities.md) describes the current model. Tables.pdf is the original reference; the migration is authoritative when names differ.
