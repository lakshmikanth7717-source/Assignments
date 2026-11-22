package com.taskmanagement.collaboration;

import com.taskmanagement.common.ApiException;
import com.taskmanagement.common.PageView;
import com.taskmanagement.task.Task;
import com.taskmanagement.task.TaskService;
import com.taskmanagement.authorization.ProjectAccess;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

import static com.taskmanagement.collaboration.CollaborationModels.*;

@Service
@Transactional
public class CollaborationService {
    private static final long MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;
    private final TaskService tasks;
    private final ProjectAccess access;
    private final CommentRepository comments;
    private final AttachmentRepository attachments;

    CollaborationService(TaskService tasks, ProjectAccess access, CommentRepository comments, AttachmentRepository attachments) {
        this.tasks = tasks;
        this.access = access;
        this.comments = comments;
        this.attachments = attachments;
    }

    @Transactional(readOnly = true)
    PageView<CommentView> comments(UUID taskId, UUID userId, Pageable page) {
        tasks.accessible(taskId, userId, false);
        return PageView.of(comments.findByTaskId(taskId, page).map(CommentView::of));
    }

    CommentView addComment(UUID taskId, UUID userId, CommentRequest request) {
        tasks.accessible(taskId, userId, true);
        return CommentView.of(comments.save(new TaskComment(taskId, userId, request.body().strip())));
    }

    CommentView updateComment(UUID taskId, UUID commentId, UUID userId, CommentRequest request) {
        tasks.accessible(taskId, userId, true);
        TaskComment comment = comments.findByIdAndTaskId(commentId, taskId).orElseThrow(() -> ApiException.notFound("Comment"));
        access.author(comment.authorId, userId);
        comment.body = request.body().strip();
        return CommentView.of(comments.saveAndFlush(comment));
    }

    void deleteComment(UUID taskId, UUID commentId, UUID userId) {
        Task task = tasks.accessible(taskId, userId, true);
        TaskComment comment = comments.findByIdAndTaskId(commentId, taskId).orElseThrow(() -> ApiException.notFound("Comment"));
        access.authorOrOwner(task.getProjectId(), comment.authorId, userId, "Only the author or project owner can delete a comment");
        comments.delete(comment);
    }

    @Transactional(readOnly = true)
    PageView<AttachmentView> attachments(UUID taskId, UUID userId, Pageable page) {
        tasks.accessible(taskId, userId, false);
        return PageView.of(attachments.findMetadataByTaskId(taskId, page));
    }

    AttachmentView upload(UUID taskId, UUID userId, MultipartFile file) throws IOException {
        tasks.accessible(taskId, userId, true);
        if (file.isEmpty()) {
            throw ApiException.badRequest("Attachment must not be empty");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "Attachments must not exceed 10 MiB");
        }
        TaskAttachment attachment = new TaskAttachment();
        attachment.taskId = taskId;
        attachment.uploaderId = userId;
        attachment.filename = safeFilename(file.getOriginalFilename());
        attachment.contentType = safeContentType(file.getContentType());
        attachment.content = file.getBytes();
        attachment.sizeBytes = attachment.content.length;
        return AttachmentView.of(attachments.save(attachment));
    }

    @Transactional(readOnly = true)
    TaskAttachment download(UUID taskId, UUID attachmentId, UUID userId) {
        tasks.accessible(taskId, userId, false);
        return attachments.findByIdAndTaskId(attachmentId, taskId).orElseThrow(() -> ApiException.notFound("Attachment"));
    }

    void deleteAttachment(UUID taskId, UUID attachmentId, UUID userId) {
        Task task = tasks.accessible(taskId, userId, true);
        TaskAttachment attachment = attachments.findByIdAndTaskId(attachmentId, taskId).orElseThrow(() -> ApiException.notFound("Attachment"));
        access.authorOrOwner(task.getProjectId(), attachment.uploaderId, userId, "Only the uploader or project owner can delete an attachment");
        attachments.delete(attachment);
    }

    private String safeFilename(String name) {
        if (name == null) {
            return "attachment";
        }
        String normalized = name.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").strip();
        if (normalized.isBlank() || normalized.equals(".") || normalized.equals("..")) {
            return "attachment";
        }
        return normalized.substring(0, Math.min(255, normalized.length()));
    }

    private String safeContentType(String value) {
        if (value == null || value.length() > 150) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(value);
            return mediaType.isConcrete() ? mediaType.toString() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
