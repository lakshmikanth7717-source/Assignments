package com.taskmanagement.project;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    UUID id = UUID.randomUUID();
    @Column(nullable = false, length = 150)
    String name;
    @Column(nullable = false, length = 5000)
    String description;
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;
    @Column(name = "invite_code", nullable = false, unique = true, length = 64)
    String inviteCode;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    protected Project() {
    }

    Project(String name, String description, UUID ownerId, String inviteCode) {
        this.name = name;
        this.description = description;
        this.ownerId = ownerId;
        this.inviteCode = inviteCode;
    }

    public UUID getOwnerId() {
        return ownerId;
    }
}
