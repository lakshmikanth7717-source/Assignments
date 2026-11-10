package com.airtribe.wallet.repository;

/** The repository must raise this only for UNIQUE(user_id, key), after a real database conflict. */
public class DuplicateIdempotencyKeyException extends RuntimeException {
    public DuplicateIdempotencyKeyException(Throwable cause) {
        super("The idempotency key already exists for this user", cause);
    }
}
