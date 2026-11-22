package com.taskmanagement.collaboration;

import com.taskmanagement.common.PageView;
import com.taskmanagement.common.Paging;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.taskmanagement.collaboration.CollaborationModels.*;
import static com.taskmanagement.authentication.CurrentUser.userId;

@RestController
@RequestMapping("/api/tasks/{taskId}")
public class CollaborationController {
    private final CollaborationService collaboration;

    CollaborationController(CollaborationService collaboration) {
        this.collaboration = collaboration;
    }

    @GetMapping("/comments")
    PageView<CommentView> comments(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId,
                                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return collaboration.comments(taskId, userId(jwt), Paging.of(page, size, "createdAt,asc", Set.of("createdAt")));
    }

    @PostMapping("/comments")
    ResponseEntity<CommentView> addComment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId,
                                           @Valid @RequestBody CommentRequest request) {
        CommentView comment = collaboration.addComment(taskId, userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/tasks/" + taskId + "/comments/" + comment.id())).body(comment);
    }

    @PutMapping("/comments/{commentId}")
    CommentView updateComment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId, @PathVariable UUID commentId,
                              @Valid @RequestBody CommentRequest request) {
        return collaboration.updateComment(taskId, commentId, userId(jwt), request);
    }

    @DeleteMapping("/comments/{commentId}")
    ResponseEntity<Void> deleteComment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId, @PathVariable UUID commentId) {
        collaboration.deleteComment(taskId, commentId, userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/attachments")
    PageView<AttachmentView> attachments(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return collaboration.attachments(taskId, userId(jwt), Paging.of(page, size, "createdAt,asc", Set.of("createdAt")));
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AttachmentView> upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId,
                                          @RequestPart("file") MultipartFile file) throws IOException {
        AttachmentView attachment = collaboration.upload(taskId, userId(jwt), file);
        return ResponseEntity.created(URI.create("/api/tasks/" + taskId + "/attachments/" + attachment.id())).body(attachment);
    }

    @GetMapping("/attachments/{attachmentId}")
    ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId, @PathVariable UUID attachmentId) {
        TaskAttachment attachment = collaboration.download(taskId, attachmentId, userId(jwt));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(attachment.sizeBytes)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(attachment.filename, StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(attachment.content);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    ResponseEntity<Void> deleteAttachment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId, @PathVariable UUID attachmentId) {
        collaboration.deleteAttachment(taskId, attachmentId, userId(jwt));
        return ResponseEntity.noContent().build();
    }
}
