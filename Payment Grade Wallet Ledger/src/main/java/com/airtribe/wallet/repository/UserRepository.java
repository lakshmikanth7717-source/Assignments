package com.airtribe.wallet.repository;

import com.airtribe.wallet.dto.UserModels.UserResponse;

import java.util.Optional;

/** Implemented by JdbcUserRepository. Every write joins the caller's transaction. */
public interface UserRepository {
    // Insert with KYC status PENDING. Translate only the email unique constraint to DuplicateEmailException.
    UserResponse create(String name, String email, String phone);

    Optional<UserResponse> findByEmail(String email);

    boolean existsById(long userId);
}
