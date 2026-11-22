package com.taskmanagement.collaboration;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_comments")
public class TaskComment {
    @Id
    UUID id = UUID.randomUUID();
    @Column(name = "task_id", nullable = false)
    UUID taskId;
    @Column(name = "author_id", nullable = false)
    UUID authorId;
    @Column(nullable = false, length = 5000)
    String body;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();

    protected TaskComment() {
    }

    TaskComment(UUID taskId, UUID authorId, String body) {
        this.taskId = taskId;
        this.authorId = authorId;
        this.body = body;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }
}
