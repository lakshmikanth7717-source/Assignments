-- Assumed P2: future active shows for a movie, city and date range, including live availability.
-- Named parameters are bound by NamedParameterJdbcTemplate, not interpolated into SQL.
WITH availability AS (
    SELECT show_id,
           SUM(CASE WHEN status = 'AVAILABLE' OR (status = 'HOLD' AND held_until <= :now)
                    THEN 1 ELSE 0 END) AS available_seats,
           MIN(CASE WHEN status = 'AVAILABLE' OR (status = 'HOLD' AND held_until <= :now)
                    THEN price ELSE NULL END) AS starting_price
    FROM show_seats
    GROUP BY show_id
)
SELECT sh.show_id, m.title, m.language, v.name AS venue, v.city, sc.name AS screen,
       sh.start_time, sh.end_time,
       COALESCE(a.available_seats, 0) AS available_seats,
       a.starting_price, 'INR' AS currency
FROM shows sh
JOIN movies m ON m.movie_id = sh.movie_id
JOIN screens sc ON sc.screen_id = sh.screen_id
JOIN venues v ON v.venue_id = sc.venue_id
LEFT JOIN availability a ON a.show_id = sh.show_id
WHERE sh.movie_id = :movie_id
  AND v.city = :city
  AND sh.status = 'ACTIVE'
  AND sh.start_time >= :day_start
  AND sh.start_time < :day_end
  AND sh.start_time > :now
ORDER BY sh.start_time, sh.show_id
