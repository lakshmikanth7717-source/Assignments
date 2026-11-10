package com.airtribe.wallet.repository.jdbc;

import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;

final class PostgresConstraints {
    private PostgresConstraints() { }

    static boolean isUniqueViolation(DataAccessException failure, String constraintName) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException postgres
                    && "23505".equals(postgres.getSQLState())
                    && postgres.getServerErrorMessage() != null
                    && constraintName.equals(postgres.getServerErrorMessage().getConstraint())) {
                return true;
            }
        }
        return false;
    }
}
