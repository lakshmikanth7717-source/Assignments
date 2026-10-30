# PR #4 review fixes

This checkout is based on PR commit 575faed71f1c9946fd3c912ff0ed639e1f9167fb. Duplicate comments are consolidated below.

| Bot comment IDs | Resolution |
| --- | --- |
| 4425424339, 4425425171 | Added ShowSeat inventory and BookingItem history. Holds now require a show, allocate inventory atomically, create a booking and return its reference. |
| 4425425163 | Physical seat uniqueness is scoped to screen, row and seat number. |
| 4425424356, 4425427087 | Holds have database deadlines, lazy reacquisition and an idempotent expiry sweep. Redis guards only the short operation. |
| 4425426576, 4425427543 | Confirmation checks booking state, show state, deadline, user, exact allocation, price total and affected-row count. Late real payments are recorded with REFUND_REQUIRED instead of reviving the booking. |
| 4425424791, 4425427096, 4425427551 | Amounts use BigDecimal end-to-end, reject fractional cents/null/out-of-range inputs and must match the booking amount and currency. |
| 4425427575 | Raw JSON is authenticated before parsing. SUCCESS and FAILED terminal outcomes follow separate paths; failed payments cannot confirm seats. |
| 4425427103 | Unexpected exceptions are logged on the server and replaced with stable caller-facing errors. |
| 4425424778 | Retained an explicit database booking foreign key; an ORM association is not required for this constraint. |
| 4425424783 | Java enums and database CHECK constraints restrict statuses; the service enforces transitions. |
| 4425425834 | Retained the correct booking-row lock for callback serialization. Inventory has atomic conditional claims and locked confirmation checks at the shared show-seat scope. |
| 4425426200, 4425427564 | Unique event and gateway-transaction constraints prevent cross-booking reuse. Existing ledger entries must match the full event. Conflicts are rejected outside the failed transactional call. |
| 4425428498, 4425428887 | Added concurrent persistence scenarios with real JPA repositories and the migrated schema, plus an explicit MySQL/Redis Testcontainers profile that does not silently skip when Docker is absent. |
| 4425425510, 4425428086 | Added executable data for every table and queries.md with a tested show-listing query. Its assumed P2 scope is documented because the exact assignment brief was unavailable. |

The former static Seat entity did not match the SQL. The Flyway migration and mapped entities now use one schema, validated in tests. Historical booking items no longer impose a permanent unique show-seat restriction that would prevent rebooking after cancellation or expiry. An explicit UTC date-time converter and UTC database sessions keep ORM and direct SQL availability reads consistent on hosts in other time zones.

The payment contract is deliberately provider-neutral: HMAC-SHA256 over the exact UTF-8 JSON bytes, hexadecimal signature, and a shared secret. A real gateway adapter must implement that provider's verification protocol before delegating. REFUND_REQUIRED records an obligation for an external refund worker; it does not claim that a refund has been issued.

Default REQUIRED propagation is retained as requested. With no caller transaction, the DB call commits before Redis unlock. An existing transaction is joined and can defer commit until after unlock; callers must respect the documented boundary.

## Verification

- Local clean verify: 36 tests passed with the actual migration, validated JPA mappings, H2 persistence and concurrent requests. The default Redis implementation is an explicit in-process test double.
- The show-listing/UTC regression also passed in a JVM configured with user.timezone=UTC, in addition to the full run on the host's Asia/Calcutta timezone.
- The MySQL/Redis profile compiled and was attempted, but could not start because this machine has no valid Docker environment. It must pass on a Docker-capable machine or the supplied CI workflow before claiming real-stack verification.
- P2 is implemented under the documented show-listing assumption; the exact original assignment brief was not provided.

These changes are prepared in the isolated review-fixes checkout on lakshmikanth/bookmyshow. The original main checkout and the other feature checkout's staged deletions were not changed. No branch or review comment was published remotely.
