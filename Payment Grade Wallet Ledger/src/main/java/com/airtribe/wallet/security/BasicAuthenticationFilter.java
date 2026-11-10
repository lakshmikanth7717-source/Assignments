package com.airtribe.wallet.security;

import com.airtribe.wallet.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.Base64;

/** Temporary HTTP Basic authentication for the local application. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@DependsOnDatabaseInitialization
public final class BasicAuthenticationFilter extends OncePerRequestFilter {
    private static final byte[] CREDENTIALS = "admin:user123".getBytes(StandardCharsets.UTF_8);
    private final long adminUserId;
    private final ObjectMapper json;

    @Autowired
    public BasicAuthenticationFilter(JdbcTemplate jdbc, ObjectMapper json) {
        this(adminUserId(jdbc), json);
    }

    BasicAuthenticationFilter(long adminUserId, ObjectMapper json) {
        this.adminUserId = adminUserId;
        this.json = json;
    }

    private static long adminUserId(JdbcTemplate jdbc) {
        // Ownership checks and admin audit records require an actual database user.
        jdbc.update("""
                INSERT INTO users (name, email, phone)
                VALUES ('admin', 'admin@digi-wallet.local', '0000000000')
                ON CONFLICT (email) DO NOTHING
                """);
        Long id = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class,
                "admin@digi-wallet.local");
        if (id == null) { throw new IllegalStateException("Administrator user was not initialized"); }
        return id;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!authenticated(request.getHeader("Authorization"))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Basic realm=\"Digi Wallet\", charset=\"UTF-8\"");
            response.setContentType("application/json");
            json.writeValue(response.getOutputStream(), new ApiError("UNAUTHENTICATED",
                    "HTTP Basic authentication is required.", RequestIdFilter.requestId(request)));
            return;
        }
        response.setHeader("X-Authenticated-User-Id", Long.toString(adminUserId));
        Principal principal = () -> Long.toString(adminUserId);
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public Principal getUserPrincipal() { return principal; }
            @Override public String getRemoteUser() { return principal.getName(); }
            @Override public String getAuthType() { return BASIC_AUTH; }
            @Override public boolean isUserInRole(String role) { return "ADMIN".equals(role); }
        }, response);
    }

    private static boolean authenticated(String authorization) {
        if (authorization == null || authorization.length() > 4096
                || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) { return false; }
        try {
            return MessageDigest.isEqual(CREDENTIALS,
                    Base64.getDecoder().decode(authorization.substring(6).trim()));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
