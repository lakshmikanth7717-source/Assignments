package com.taskmanagement.authentication;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    UUID id = UUID.randomUUID();
    @Column(nullable = false, unique = true, length = 254)
    String email;
    @Column(nullable = false, length = 100)
    String name;
    @Column(name = "password_hash", nullable = false, length = 100)
    String passwordHash;
    @Column(name = "token_version", nullable = false)
    int tokenVersion;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    protected AppUser() {
    }

    AppUser(String email, String name, String passwordHash) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
