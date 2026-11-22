package com.taskmanagement.collaboration;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public final class CollaborationModels {
    private CollaborationModels() {
    }

    public record CommentRequest(@NotBlank @Size(max = 5000) String body) {
    }

    public record CommentView(UUID id, UUID taskId, UUID authorId, String body, Instant createdAt, Instant updatedAt) {
        static CommentView of(TaskComment comment) {
            return new CommentView(comment.id, comment.taskId, comment.authorId, comment.body, comment.createdAt, comment.updatedAt);
        }
    }

    public record AttachmentView(UUID id, UUID taskId, UUID uploaderId, String filename,
                                 String contentType, long sizeBytes, Instant createdAt) {
        static AttachmentView of(TaskAttachment attachment) {
            return new AttachmentView(attachment.id, attachment.taskId, attachment.uploaderId, attachment.filename,
                attachment.contentType, attachment.sizeBytes, attachment.createdAt);
        }
    }
}
