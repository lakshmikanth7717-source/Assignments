# BookMyShow Booking Engine

A Spring Boot service prototype for temporary seat reservations and idempotent payment processing. It uses Redis locks to coordinate seat holds and database transactions to group payment, booking, and seat confirmation updates.

The current implementation provides Java service methods and unit tests. REST controllers, a user interface, and payment gateway integration are not implemented.

## Technology stack

| Component | Technology |
| --- | --- |
| Language | Java 17 |
| Application framework | Spring Boot 3.2.5 |
| Persistence | Spring Data JPA / Hibernate |
| Database | MySQL |
| Distributed locking | Redis through Redisson 3.28.0 |
| Build | Maven |
| Tests | JUnit 5 and Mockito |

## Project structure

```text
BookMyShow/
├── Readme.md
├── entities.md                         # Domain design notes
├── Tables.pdf                          # Additional database documentation
└── src/
    ├── pom.xml                         # Maven project root
    ├── main/
    │   ├── java/
    │   │   ├── main/
    │   │   │   └── BookMyShowApplication.java
    │   │   ├── entity/
    │   │   │   ├── Booking.java
    │   │   │   ├── Payment.java
    │   │   │   └── Seat.java
    │   │   ├── repo/
    │   │   │   ├── BookingRepository.java
    │   │   │   ├── PaymentRepository.java
    │   │   │   └── SeatRepository.java
    │   │   └── service/
    │   │       ├── BookingService.java
    │   │       ├── BookingTransactionService.java
    │   │       ├── BookingResult.java
    │   │       └── WebhookResult.java
    │   └── resources/
    │       └── V1__create_booking_engine_schema.sql
    ├── test/java/service/
    │   ├── BookingServiceTest.java
    │   └── BookingTransactionIntegrationTest.java
    └── target/                         # Generated build output
```

The POM explicitly configures `main/java`, `main/resources`, and `test/java` relative to `src/`. Run the commands below from the repository root, using `-f src/pom.xml`.

## Service behavior

### Hold a seat for five minutes

```java
BookingResult result = bookingService.holdSeatForFiveMinutes(seatId, userId);
```

1. Obtain the Redis lock named `lock:seat:<seatId>`, waiting up to 10 seconds.
2. After acquiring the lock, call the database service, which starts a transaction or joins an existing one.
3. Conditionally update the seat if its status is `AVAILABLE`, or if it is in `HOLD` and its previous hold has expired.
4. Set the status to `HOLD`, record the user, and set `heldUntil` to five minutes after the current time.
5. With no existing caller transaction, commit before releasing the Redis lock. If a caller transaction exists, its completion determines when the changes commit.

Redisson's watchdog maintains the Redis lock while the operation runs. The five-minute reservation is tracked in the database; it is separate from the Redis lock lifetime.

Lock contention, an unavailable seat, and thread interruption produce a failed `BookingResult`. Interruption preserves the thread's interrupted flag. Database failures roll back and propagate to the caller; the lock is released if the current thread still owns it.

Expired holds become eligible for a later hold request. There is no scheduled expiration worker.

### Process a payment callback

```java
WebhookResult result = bookingService.processPaymentWebhookIdempotently(
    "evt_998877", "booking-123", "gateway-txn-456", 500.00
);
```

The referenced booking and its booking items must already exist. This method does not create a booking or charge a payment method.

Within one transaction, the service:

1. Loads the booking with a pessimistic write lock, serializing callbacks for that booking.
2. Looks up the payment by its idempotency key.
3. Rejects a key associated with another booking.
4. Returns success without further updates for an existing `SUCCESS` or `FAILED` payment, or an already confirmed booking.
5. Creates or updates the payment ledger with status `SUCCESS`.
6. Changes the booking status to `CONFIRMED`.
7. Updates matching `show_seats` through `booking_items`, setting them to `BOOKED` and clearing their lock owner and expiry.

With no existing caller transaction, runtime failures roll back the transaction before a failed `WebhookResult` is returned. If the method joins a caller transaction, a runtime failure marks that shared transaction rollback-only; the caller may receive `UnexpectedRollbackException` when it tries to commit. The SQL design includes a unique constraint on `payments.idempotency_key`; callbacks for different bookings that race to reuse a key may fail that constraint.

Both result types are records with `success()` and `message()` accessors. These methods are service entry points, not HTTP endpoints.

### Transaction propagation

`BookingService` coordinates Redis locking and converts webhook failures into responses. It calls the separate Spring bean `BookingTransactionService`, whose public `holdSeat` and `processPayment` methods use plain `@Transactional`, with the default `REQUIRED` propagation.

When no transaction exists, the database operation starts one and completes its commit or rollback before returning to `BookingService`. This is the current production entry flow: the hold commits before Redis unlock, and webhook errors are caught outside the transactional proxy.

If a transactional caller is introduced, the database service joins that existing transaction. An outer rollback then undoes the hold or payment updates too. A caught runtime failure in the database service still marks the shared transaction rollback-only, so the outer workflow cannot commit its remaining writes.

Joining an outer transaction also defers commit until that outer transaction completes, which can occur after Redis unlock. The separate bean ensures Spring intercepts the calls; it does not create an independent transaction when one already exists.

## Build and test

Install a JDK with Java 17 support and Maven, and configure `JAVA_HOME`. No Maven wrapper is included.

```shell
# Compile, run tests, and build the executable JAR
mvn -f src/pom.xml clean verify

# Run the service tests only
mvn -f src/pom.xml -Dtest=service.BookingServiceTest test
```

Nine service tests invoke the database service through Spring's annotation-based transaction interceptor, with mocked Redis, repositories, and transaction manager. They do not require running MySQL or Redis instances. They cover:

- Five-minute expiry and committing before unlocking.
- Lock contention without database access.
- Preservation of the interrupted flag.
- Rollback and lock release after a hold failure.
- Successful payment processing and booking confirmation.
- Duplicate callback handling without repeated writes.
- Rollback after a seat confirmation failure.
- Rejection of idempotency keys belonging to another booking.
- Handling commit failures outside the transactional proxy.

Four additional integration tests use a Spring application context, real JDBC transactions, and an in-memory H2 database. Repository mocks write event rows through JDBC to verify:

- A standalone seat hold is visible on a separate connection before Redis unlock.
- A seat hold joins an existing transaction and rolls back when the outer workflow fails.
- Payment, booking, and seat event rows roll back with an outer workflow.
- A caught payment failure marks the outer transaction rollback-only and causes `UnexpectedRollbackException` at commit.

Run all 13 tests with `mvn -f src/pom.xml test`. These tests verify transaction boundaries; they do not validate the production JPA mappings, MySQL native SQL, or live Redis integration.

The packaged application is written to:

```text
src/target/booking-engine-0.0.1-SNAPSHOT.jar
```

## Runtime setup

Running the application requires MySQL, Redis, connection configuration, and a compatible database schema. No application configuration file or credentials are committed.

Before running, resolve the schema differences described below. Then provide configuration through environment variables, for example in PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:3306/booking_engine"
$env:SPRING_DATASOURCE_USERNAME = "booking_user"
$env:SPRING_DATASOURCE_PASSWORD = "<your-local-password>"
$env:SPRING_DATA_REDIS_HOST = "localhost"
$env:SPRING_DATA_REDIS_PORT = "6379"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = "validate"

mvn -f src/pom.xml spring-boot:run
```

Alternatively, after packaging:

```shell
java -jar src/target/booking-engine-0.0.1-SNAPSHOT.jar
```

In IntelliJ IDEA, import `src/pom.xml` as the Maven project and use `src/main/java` as the production source root and `src/test/java` as the test source root. The application entry point is `main.BookMyShowApplication`.

## Database design and current limitations

[The SQL design](src/main/resources/V1__create_booking_engine_schema.sql) defines `shows`, `seats`, `show_seats`, `bookings`, `booking_items`, and `payments`. Only `Seat`, `Booking`, and `Payment` currently have JPA entities. See [entities.md](entities.md) for the broader domain design.

The SQL file is a design artifact, not an automatically applied migration: Flyway is not a dependency, and the file is not under `db/migration`.

The seat model and SQL design need reconciliation before using the service against that schema:

- `Seat` expects `seats.id`, a string `seat_number`, and the columns `status`, `held_by_user_id`, and `held_until`.
- The SQL defines `seats.seat_id`, an integer `seat_number`, and physical layout fields. It stores reservation state in `show_seats` instead.
- The hold method updates `seats` using only a seat ID, while payment confirmation updates `show_seats` using booking items. The two operations do not currently share one reservation-state model.

Additional work needed for a complete booking application includes show-specific reservations, booking/item creation, HTTP endpoints, callback authentication, payment amount validation, and checks for booking expiry and current seat ownership before confirmation. Payment amounts are stored as `BigDecimal`, although the current callback method accepts `Double`.

A successful build verifies compilation, unit tests, and packaging. It does not establish that the current schema and service can run together unchanged.
