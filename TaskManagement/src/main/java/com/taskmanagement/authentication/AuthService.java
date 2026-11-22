package com.taskmanagement.authentication;

import com.taskmanagement.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import static com.taskmanagement.authentication.AuthModels.*;

@Service
@Transactional
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration ttl;
    private final String dummyPassword;

    AuthService(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder,
                @Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.ttl}") Duration ttl) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttl = ttl;
        this.dummyPassword = passwords.encode(UUID.randomUUID().toString());
    }

    AuthView register(RegisterRequest request) {
        validatePassword(request.password());
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("Email is already registered");
        }
        AppUser user = users.saveAndFlush(new AppUser(email, request.name().strip(), passwords.encode(request.password())));
        return tokens(user);
    }

    @Transactional(readOnly = true)
    AuthView login(LoginRequest request) {
        validatePassword(request.password());
        AppUser user = users.findByEmail(normalizeEmail(request.email())).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? dummyPassword : user.passwordHash);
        if (!matches || user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return tokens(user);
    }

    @Transactional(readOnly = true)
    UserView profile(UUID userId) {
        return UserView.of(user(userId));
    }

    UserView updateProfile(UUID userId, ProfileRequest request) {
        AppUser user = user(userId);
        user.name = request.name().strip();
        return UserView.of(user);
    }

    void changePassword(UUID userId, PasswordRequest request) {
        AppUser user = users.findLockedById(userId).orElseThrow(() -> ApiException.notFound("User"));
        validatePassword(request.currentPassword());
        validatePassword(request.newPassword());
        if (!passwords.matches(request.currentPassword(), user.passwordHash)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        user.passwordHash = passwords.encode(request.newPassword());
        user.tokenVersion++;
    }

    private AppUser user(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Password must not exceed 72 UTF-8 bytes");
        }
    }

    private AuthView tokens(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(user.id.toString())
            .issuedAt(now).expiresAt(now.plus(ttl)).id(UUID.randomUUID().toString()).claim("ver", user.tokenVersion).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthView(token, "Bearer", ttl.toSeconds(), UserView.of(user));
    }
}
