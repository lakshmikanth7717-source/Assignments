package com.airtribe.wallet.repository.jdbc;

import com.airtribe.wallet.dto.UserModels.KycStatus;
import com.airtribe.wallet.dto.UserModels.UserResponse;
import com.airtribe.wallet.repository.DuplicateEmailException;
import com.airtribe.wallet.repository.UserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcUserRepository implements UserRepository {
    private static final RowMapper<UserResponse> USER = (row, index) -> new UserResponse(
            row.getLong("user_id"), row.getString("name"), row.getString("email"),
            row.getString("phone"), KycStatus.valueOf(row.getString("kyc_status")));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcUserRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public UserResponse create(String name, String email, String phone) {
        try {
            return jdbc.queryForObject("""
                    INSERT INTO users (name, email, phone)
                    VALUES (:name, :email, :phone)
                    RETURNING user_id, name, email, phone, kyc_status
                    """, Map.of("name", name, "email", email, "phone", phone), USER);
        } catch (DataAccessException failure) {
            if (PostgresConstraints.isUniqueViolation(failure, "users_email_unique")) {
                throw new DuplicateEmailException(failure);
            }
            throw failure;
        }
    }

    @Override
    public Optional<UserResponse> findByEmail(String email) {
        return jdbc.query("""
                SELECT user_id, name, email, phone, kyc_status
                FROM users WHERE email = :email
                """, Map.of("email", email), USER).stream().findFirst();
    }

    @Override
    public boolean existsById(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE user_id = :userId)",
                Map.of("userId", userId), Boolean.class));
    }
}
