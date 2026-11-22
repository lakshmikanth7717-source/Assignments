package com.taskmanagement.project;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_members", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
public class ProjectMember {
    @Id
    UUID id = UUID.randomUUID();
    @Column(name = "project_id", nullable = false)
    UUID projectId;
    @Column(name = "user_id", nullable = false)
    UUID userId;
    @Column(name = "joined_at", nullable = false, updatable = false)
    Instant joinedAt = Instant.now();

    protected ProjectMember() {
    }

    ProjectMember(UUID projectId, UUID userId) {
        this.projectId = projectId;
        this.userId = userId;
    }
}
