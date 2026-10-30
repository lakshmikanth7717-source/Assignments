# Domain model

| Entity/table | Key and responsibility |
| --- | --- |
| venues | Venue ID; city and address |
| screens | Screen ID; belongs to a venue; name unique within venue |
| movies | Movie ID; title, language and duration |
| Show / shows | Show ID; movie, screen, start/end time and active/cancelled state |
| Seat / seats | Physical seat ID; screen, row, number and category; screen/row/number is unique |
| ShowSeat / show_seats | Composite show/seat ID; price, availability, owning booking/user and hold deadline |
| Booking / bookings | Booking ID; user, show, status, total, currency and expiry |
| BookingItem / booking_items | Allocation history and price snapshot; booking/show/seat is unique |
| Payment / payments | Terminal ledger; unique event and transaction references; amount, currency, outcome and resolution |

A physical seat has no reservation status. A show-seat is AVAILABLE, HOLD or BOOKED. AVAILABLE has no owner; HOLD has a booking, user and deadline; BOOKED retains the booking and clears temporary hold fields. SQL CHECK constraints enforce these shapes.

Bookings transition from PENDING to CONFIRMED, CANCELLED or EXPIRED. Expired/cancelled bookings cannot be revived by payment. Historical items do not impose a permanent unique show-seat constraint across bookings.

Payment outcomes are SUCCESS or FAILED. Resolutions are APPLIED, DECLINED or REFUND_REQUIRED. A late successful payment records a refund obligation without claiming inventory. Actual refund execution belongs to a separate integration.

The authoritative schema is [the Flyway migration](src/main/resources/db/migration/V1__create_booking_engine_schema.sql). Amounts use DECIMAL(10,2) / BigDecimal and timestamps use UTC. See [sample data](src/main/resources/sql/sample-data.sql) and [query documentation](queries.md).
