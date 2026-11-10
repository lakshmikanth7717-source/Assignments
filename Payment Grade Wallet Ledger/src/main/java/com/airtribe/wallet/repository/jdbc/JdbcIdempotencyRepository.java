package com.airtribe.wallet.repository.jdbc;

import com.airtribe.wallet.repository.DuplicateIdempotencyKeyException;
import com.airtribe.wallet.repository.IdempotencyRepository;
import com.airtribe.wallet.service.StoredHttpResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcIdempotencyRepository implements IdempotencyRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcIdempotencyRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void insert(long userId, UUID key, byte[] requestHash) {
        try {
            // Plain INSERT executes now: the unique index coordinates competing requests.
            jdbc.update("""
                    INSERT INTO idempotency_keys (user_id, key, request_hash)
                    VALUES (:userId, :key, :hash)
                    """, Map.of("userId", userId, "key", key, "hash", requestHash));
        } catch (DataAccessException failure) {
            if (PostgresConstraints.isUniqueViolation(failure, "idempotency_keys_user_key_unique")) {
                throw new DuplicateIdempotencyKeyException(failure);
            }
            throw failure;
        }
    }

    @Override
    public Optional<SavedRequest> findByUserAndKey(long userId, UUID key) {
        return jdbc.query("""
                SELECT request_hash, status, response_code, response_body
                FROM idempotency_keys WHERE user_id = :userId AND key = :key
                """, Map.of("userId", userId, "key", key), (row, index) -> {
            Status status = Status.valueOf(row.getString("status"));
            StoredHttpResponse response = status == Status.COMPLETED
                    ? new StoredHttpResponse(row.getInt("response_code"), row.getString("response_body"))
                    : null;
            return new SavedRequest(row.getBytes("request_hash"), status, response);
        }).stream().findFirst();
    }

    @Override
    public void complete(long userId, UUID key, StoredHttpResponse response) {
        int updated = jdbc.update("""
                UPDATE idempotency_keys
                SET status = 'COMPLETED', response_code = :code, response_body = :body
                WHERE user_id = :userId AND key = :key AND status = 'IN_PROGRESS'
                """, Map.of("userId", userId, "key", key,
                "code", response.statusCode(), "body", response.body()));
        if (updated != 1) { throw new IllegalStateException("Expected to complete exactly one idempotency key"); }
    }
}
