package com.taskmanagement.collaboration;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<TaskAttachment, UUID> {
    // A projection prevents list responses from loading the binary content.
    @Query("select new com.taskmanagement.collaboration.CollaborationModels$AttachmentView(a.id, a.taskId, a.uploaderId, a.filename, a.contentType, a.sizeBytes, a.createdAt) from TaskAttachment a where a.taskId = :taskId")
    Page<CollaborationModels.AttachmentView> findMetadataByTaskId(UUID taskId, Pageable pageable);

    Optional<TaskAttachment> findByIdAndTaskId(UUID id, UUID taskId);
}
