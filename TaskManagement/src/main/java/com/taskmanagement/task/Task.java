package com.taskmanagement.task;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {
    public enum Status {TODO, IN_PROGRESS, DONE}

    public enum Priority {LOW, MEDIUM, HIGH, URGENT}

    @Id
    UUID id = UUID.randomUUID();
    @Column(name = "project_id", nullable = false)
    UUID projectId;
    @Column(nullable = false, length = 200)
    String title;
    @Column(nullable = false, length = 10000)
    String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    Status status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    Priority priority;
    @Column(name = "due_date")
    LocalDate dueDate;
    @Column(name = "creator_id", nullable = false)
    UUID creatorId;
    @Column(name = "assignee_id")
    UUID assigneeId;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();
    @Version
    long version;

    protected Task() {
    }

    Task(UUID projectId, UUID creatorId) {
        this.projectId = projectId;
        this.creatorId = creatorId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }
}
