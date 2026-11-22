package com.taskmanagement.authentication;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public final class AuthModels {
    private AuthModels() {
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(max = 100) String name,
                                  @NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 72) String password) {
    }

    public record ProfileRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record PasswordRequest(@NotBlank @Size(max = 72) String currentPassword,
                                  @NotBlank @Size(min = 12, max = 72) String newPassword) {
    }

    public record UserView(UUID id, String email, String name, Instant createdAt) {
        static UserView of(AppUser user) {
            return new UserView(user.id, user.email, user.name, user.createdAt);
        }
    }

    public record AuthView(String accessToken, String tokenType, long expiresIn, UserView user) {
    }
}
