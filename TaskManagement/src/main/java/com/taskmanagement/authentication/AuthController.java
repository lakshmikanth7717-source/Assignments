package com.taskmanagement.authentication;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import static com.taskmanagement.authentication.AuthModels.*;
import static com.taskmanagement.authentication.CurrentUser.userId;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/register")
    ResponseEntity<AuthView> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.created(URI.create("/api/users/me")).body(auth.register(request));
    }

    @PostMapping("/auth/login")
    AuthView login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @GetMapping("/users/me")
    UserView profile(@AuthenticationPrincipal Jwt jwt) {
        return auth.profile(userId(jwt));
    }

    @PatchMapping("/users/me")
    UserView updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
        return auth.updateProfile(userId(jwt), request);
    }

    @PutMapping("/users/me/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordRequest request) {
        auth.changePassword(userId(jwt), request);
        return ResponseEntity.noContent().build();
    }
}
