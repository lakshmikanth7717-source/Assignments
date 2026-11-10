package com.airtribe.wallet.service;

import com.airtribe.wallet.dto.UserModels.CreateUserRequest;
import com.airtribe.wallet.dto.UserModels.UserResponse;
import com.airtribe.wallet.exception.ApiException;
import com.airtribe.wallet.repository.DuplicateEmailException;
import com.airtribe.wallet.repository.IdempotencyRepository;
import com.airtribe.wallet.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository users;
    private final IdempotencyRepository keys;
    private final IdempotencyService idempotency;

    public UserService(UserRepository users, IdempotencyRepository keys, IdempotencyService idempotency) {
        this.users = users;
        this.keys = keys;
        this.idempotency = idempotency;
    }

    public StoredHttpResponse createUser(UUID idempotencyKey, CreateUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        byte[] hash = idempotency.requestHash(new IdempotencyService.RequestFingerprint("SIGNUP", request));
        try {
            return idempotency.inTransaction(() -> {
                // Signup needs the new user ID before its key can reference users.user_id.
                // User creation, key insertion and completed response still commit together.
                UserResponse user = users.create(request.name().trim(), email, request.phone().trim());
                keys.insert(user.userId(), idempotencyKey, hash);
                StoredHttpResponse response = idempotency.created(user);
                keys.complete(user.userId(), idempotencyKey, response);
                return response;
            });
        } catch (DuplicateEmailException duplicate) {
            // Also handles two concurrent signup attempts: the losing INSERT rolls back first.
            return idempotency.inTransaction(() -> {
                UserResponse existing = users.findByEmail(email)
                        .orElseThrow(() -> new IllegalStateException("Conflicting email was not found"));
                return idempotency.replay(keys.findByUserAndKey(existing.userId(), idempotencyKey)
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS",
                                "A user with this email already exists.")), hash);
            });
        }
    }
}
