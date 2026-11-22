package com.taskmanagement.collaboration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<TaskComment, UUID> {
    Page<TaskComment> findByTaskId(UUID taskId, Pageable pageable);

    Optional<TaskComment> findByIdAndTaskId(UUID id, UUID taskId);
}
