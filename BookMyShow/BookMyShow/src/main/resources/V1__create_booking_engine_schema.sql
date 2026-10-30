#1. Shows Table

CREATE TABLE shows (
    show_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    movie_id BIGINT NOT NULL,
    screen_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- Default status is ACTIVE
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Auto-populated creation time
    INDEX idx_movie_time (movie_id, start_time)
);

# 2. Seats Table (Static Layout)

CREATE TABLE seats (
    seat_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    screen_id BIGINT NOT NULL,
    row_identifier VARCHAR(5) NOT NULL, 
    seat_number INT NOT NULL,           
    seat_category VARCHAR(20) NOT NULL DEFAULT 'NORMAL', -- Default seat tier is NORMAL
    UNIQUE KEY uk_screen_seat (screen_id, row_identifier, seat_number)
);

#3. Show Seats Table (Dynamic Locking & High Concurrency State)

CREATE TABLE show_seats (
    show_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', -- New seats start as AVAILABLE
    locked_by_user_id BIGINT DEFAULT NULL,           -- NULL until a user locks it
    lock_expires_at TIMESTAMP NULL DEFAULT NULL,     -- NULL until locked
    version INT NOT NULL DEFAULT 1,                  -- Starts at version 1 for optimistic locking
    PRIMARY KEY (show_id, seat_id),
    INDEX idx_lock_expiry (lock_expires_at, status)
);

# 4. Bookings Table
CREATE TABLE bookings (
    booking_id VARCHAR(64) PRIMARY KEY, 
    user_id BIGINT NOT NULL,
    show_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',    -- New bookings start as PENDING
    total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    expires_at TIMESTAMP NOT NULL,                  -- Expiration timer for the booking
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Auto-updates on modification
    INDEX idx_user_bookings (user_id),
    INDEX idx_expiry_status (status, expires_at)
);

# 5. Booking Items Table
CREATE TABLE booking_items (
    booking_item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id VARCHAR(64) NOT NULL,
    show_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    FOREIGN KEY (booking_id) REFERENCES bookings(booking_id),
    UNIQUE KEY uk_show_seat (show_id, seat_id)
);

# 6. Payments Table (Idempotent Webhooks Support)
CREATE TABLE payments (
    payment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id VARCHAR(64) NOT NULL,
    gateway_transaction_id VARCHAR(128) DEFAULT NULL,    -- NULL until gateway responds
    idempotency_key VARCHAR(128) UNIQUE NOT NULL,        -- Required for safe webhook retries
    amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',     -- Default status before gateway callback
    gateway_response_payload TEXT DEFAULT NULL,          -- JSON payload storage
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (booking_id) REFERENCES bookings(booking_id)
);