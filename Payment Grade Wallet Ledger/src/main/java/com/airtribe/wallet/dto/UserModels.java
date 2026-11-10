package com.airtribe.wallet.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserModels {
    private UserModels() { }

    public record CreateUserRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 20) String phone) { }

    public enum KycStatus { PENDING, VERIFIED, REJECTED }

    public record UserResponse(long userId, String name, String email, String phone, KycStatus kycStatus) { }
}
