package com.taskmanagement.collaboration;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_attachments")
public class TaskAttachment {
    @Id
    UUID id = UUID.randomUUID();
    @Column(name = "task_id", nullable = false)
    UUID taskId;
    @Column(name = "uploader_id", nullable = false)
    UUID uploaderId;
    @Column(nullable = false, length = 255)
    String filename;
    @Column(name = "content_type", nullable = false, length = 150)
    String contentType;
    @Column(name = "size_bytes", nullable = false)
    long sizeBytes;
    @Column(nullable = false)
    byte[] content;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    protected TaskAttachment() {
    }
}
