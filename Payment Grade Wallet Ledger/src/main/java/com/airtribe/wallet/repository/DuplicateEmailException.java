package com.airtribe.wallet.repository;

/** The repository must raise this only for the users.email unique constraint. */
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(Throwable cause) {
        super("A user with this email already exists", cause);
    }
}
