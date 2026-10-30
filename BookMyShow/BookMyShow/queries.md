# Show-listing query (P2 assumption)

The original assignment brief is not in this repository. This implements the stated show-listing deliverable with the following explicit assumption: list future active shows for a selected movie and city within a selected date range, with venue, screen, language, availability and starting price. Adjust the filters if the original P2 requires a different result.

The executable query is [list-shows.sql](src/main/resources/sql/list-shows.sql). It targets MySQL 8 and uses these bound parameters:

| Parameter | Meaning |
| --- | --- |
| movie_id | Selected movie ID |
| city | Selected venue city |
| day_start | Inclusive start of the requested day, expressed in UTC |
| day_end | Exclusive end of the requested day, expressed in UTC |
| now | Current UTC timestamp |

The caller must convert the user's selected local date into the appropriate UTC boundaries. Do not apply a date function to start_time; the range filter can use the movie/time index.

An expired HOLD counts as available even before the expiry worker runs. BOOKED seats never count as available. Sold-out shows are retained with zero available seats and a null starting price.

All seeded prices use INR. JDBC callers should use NamedParameterJdbcTemplate with bound values rather than string substitution. The persistence scenarios execute this query against the same migrated schema used by the service.

[Sample data](src/main/resources/sql/sample-data.sql) provides executable examples for all tables. It is optional and is not loaded in production.
