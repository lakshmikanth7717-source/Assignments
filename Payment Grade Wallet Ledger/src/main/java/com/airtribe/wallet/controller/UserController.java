package com.airtribe.wallet.controller;

import com.airtribe.wallet.dto.UserModels.CreateUserRequest;
import com.airtribe.wallet.service.StoredHttpResponse;
import com.airtribe.wallet.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) { this.users = users; }

    @PostMapping
    public ResponseEntity<String> createUser(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreateUserRequest request) {
        StoredHttpResponse response = users.createUser(idempotencyKey, request);
        return response.toResponseEntity();
    }
}
