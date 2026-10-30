-- Optional development examples for every table. Never loaded automatically by Flyway.
-- Uses reserved demo IDs; import once into a fresh development database after migration.
INSERT INTO venues(venue_id,name,city,address)
VALUES (201,'Demo Cinemas','Bengaluru','Demo Road');

INSERT INTO screens(screen_id,venue_id,name)
VALUES (301,201,'Screen 1'),(302,201,'Screen 2');

INSERT INTO movies(movie_id,title,language,duration_minutes)
VALUES (501,'Demo Adventure','English',150);

INSERT INTO shows(show_id,movie_id,screen_id,start_time,end_time,status)
VALUES (101,501,301,CURRENT_TIMESTAMP + INTERVAL '1' DAY,
        CURRENT_TIMESTAMP + INTERVAL '1' DAY + INTERVAL '3' HOUR,'ACTIVE'),
       (102,501,301,CURRENT_TIMESTAMP + INTERVAL '2' DAY,
        CURRENT_TIMESTAMP + INTERVAL '2' DAY + INTERVAL '3' HOUR,'ACTIVE'),
       (103,501,302,CURRENT_TIMESTAMP + INTERVAL '1' DAY,
        CURRENT_TIMESTAMP + INTERVAL '1' DAY + INTERVAL '3' HOUR,'ACTIVE');

INSERT INTO seats(seat_id,screen_id,row_identifier,seat_number,seat_category)
VALUES (1001,301,'A',1,'NORMAL'),(1002,301,'A',2,'NORMAL'),
       (1003,301,'E',10,'VIP'),(1004,302,'A',1,'NORMAL');

INSERT INTO bookings(booking_id,user_id,show_id,status,total_amount,currency,expires_at)
VALUES ('demo-pending',4512,101,'PENDING',100.00,'INR',CURRENT_TIMESTAMP + INTERVAL '5' MINUTE),
       ('demo-confirmed',9821,101,'CONFIRMED',250.00,'INR',CURRENT_TIMESTAMP + INTERVAL '5' MINUTE),
       ('demo-cancelled',9910,102,'CANCELLED',100.00,'INR',CURRENT_TIMESTAMP + INTERVAL '5' MINUTE);

INSERT INTO show_seats(show_id,seat_id,price,status,held_by_user_id,held_until,booking_id,version)
VALUES (101,1001,100.00,'AVAILABLE',NULL,NULL,NULL,0),
       (101,1002,100.00,'HOLD',4512,CURRENT_TIMESTAMP + INTERVAL '5' MINUTE,'demo-pending',1),
       (101,1003,250.00,'BOOKED',NULL,NULL,'demo-confirmed',2),
       (102,1001,100.00,'AVAILABLE',NULL,NULL,NULL,2),
       (103,1004,100.00,'AVAILABLE',NULL,NULL,NULL,0);

INSERT INTO booking_items(booking_id,show_id,seat_id,price)
VALUES ('demo-pending',101,1002,100.00),('demo-confirmed',101,1003,250.00),
       ('demo-cancelled',102,1001,100.00);

INSERT INTO payments(booking_id,gateway_transaction_id,idempotency_key,amount,currency,status,resolution)
VALUES ('demo-confirmed','demo-txn-1','demo-event-1',250.00,'INR','SUCCESS','APPLIED'),
       ('demo-cancelled','demo-txn-2','demo-event-2',100.00,'INR','FAILED','DECLINED');
