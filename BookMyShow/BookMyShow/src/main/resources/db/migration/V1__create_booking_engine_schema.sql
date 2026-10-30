-- MySQL 8 schema. Reservation state belongs to show_seats, never the physical seats table.
CREATE TABLE venues (
    venue_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    city VARCHAR(80) NOT NULL,
    address VARCHAR(255) NOT NULL
);

CREATE TABLE screens (
    screen_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    CONSTRAINT fk_screen_venue FOREIGN KEY (venue_id) REFERENCES venues(venue_id),
    CONSTRAINT uk_venue_screen UNIQUE (venue_id, name)
);

CREATE TABLE movies (
    movie_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(160) NOT NULL,
    language VARCHAR(40) NOT NULL,
    duration_minutes INT NOT NULL,
    CONSTRAINT ck_movie_duration CHECK (duration_minutes > 0)
);

CREATE TABLE shows (
    show_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    movie_id BIGINT NOT NULL,
    screen_id BIGINT NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_show_movie FOREIGN KEY (movie_id) REFERENCES movies(movie_id),
    CONSTRAINT fk_show_screen FOREIGN KEY (screen_id) REFERENCES screens(screen_id),
    CONSTRAINT ck_show_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_show_times CHECK (end_time > start_time)
);
CREATE INDEX idx_show_movie_time ON shows(movie_id, start_time);

CREATE TABLE seats (
    seat_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    screen_id BIGINT NOT NULL,
    row_identifier VARCHAR(5) NOT NULL,
    seat_number INT NOT NULL,
    seat_category VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    CONSTRAINT fk_seat_screen FOREIGN KEY (screen_id) REFERENCES screens(screen_id),
    CONSTRAINT uk_screen_seat UNIQUE (screen_id, row_identifier, seat_number),
    CONSTRAINT ck_seat_number CHECK (seat_number > 0),
    CONSTRAINT ck_seat_category CHECK (seat_category IN ('NORMAL', 'VIP', 'EXECUTIVE'))
);

CREATE TABLE bookings (
    booking_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    show_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_booking_show FOREIGN KEY (show_id) REFERENCES shows(show_id),
    CONSTRAINT uk_booking_show UNIQUE (booking_id, show_id),
    CONSTRAINT ck_booking_status CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_booking_amount CHECK (total_amount >= 0)
);
CREATE INDEX idx_user_bookings ON bookings(user_id);
CREATE INDEX idx_booking_expiry ON bookings(status, expires_at);

CREATE TABLE show_seats (
    show_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    held_by_user_id BIGINT NULL,
    held_until DATETIME(6) NULL,
    booking_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (show_id, seat_id),
    CONSTRAINT fk_inventory_show FOREIGN KEY (show_id) REFERENCES shows(show_id),
    CONSTRAINT fk_inventory_seat FOREIGN KEY (seat_id) REFERENCES seats(seat_id),
    CONSTRAINT fk_inventory_booking FOREIGN KEY (booking_id, show_id) REFERENCES bookings(booking_id, show_id),
    CONSTRAINT ck_inventory_price CHECK (price >= 0),
    CONSTRAINT ck_inventory_state CHECK (
        (status = 'AVAILABLE' AND booking_id IS NULL AND held_by_user_id IS NULL AND held_until IS NULL) OR
        (status = 'HOLD' AND booking_id IS NOT NULL AND held_by_user_id IS NOT NULL AND held_until IS NOT NULL) OR
        (status = 'BOOKED' AND booking_id IS NOT NULL AND held_by_user_id IS NULL AND held_until IS NULL)
    )
);
CREATE INDEX idx_inventory_expiry ON show_seats(status, held_until);
CREATE INDEX idx_inventory_booking ON show_seats(booking_id);

CREATE TABLE booking_items (
    booking_item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id VARCHAR(64) NOT NULL,
    show_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_item_booking FOREIGN KEY (booking_id, show_id) REFERENCES bookings(booking_id, show_id),
    CONSTRAINT fk_item_inventory FOREIGN KEY (show_id, seat_id) REFERENCES show_seats(show_id, seat_id),
    -- Keep historical items after expiry/cancellation; only live inventory has exclusive ownership.
    CONSTRAINT uk_booking_seat UNIQUE (booking_id, show_id, seat_id),
    CONSTRAINT ck_item_price CHECK (price >= 0)
);

CREATE TABLE payments (
    payment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id VARCHAR(64) NOT NULL,
    gateway_transaction_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    resolution VARCHAR(20) NOT NULL,
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES bookings(booking_id),
    CONSTRAINT uk_payment_event UNIQUE (idempotency_key),
    CONSTRAINT uk_payment_transaction UNIQUE (gateway_transaction_id),
    CONSTRAINT ck_payment_amount CHECK (amount >= 0),
    CONSTRAINT ck_payment_resolution CHECK (
        (status = 'SUCCESS' AND resolution IN ('APPLIED', 'REFUND_REQUIRED')) OR
        (status = 'FAILED' AND resolution = 'DECLINED')
    )
);
CREATE INDEX idx_payment_refunds ON payments(resolution);
