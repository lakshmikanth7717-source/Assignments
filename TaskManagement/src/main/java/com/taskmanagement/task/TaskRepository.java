package com.taskmanagement.task;

import org.springframework.data.jpa.repository.*;

import java.util.UUID;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {
    @Query("select t.projectId from Task t where t.id = :id")
    Optional<UUID> findProjectIdById(UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Task t set t.assigneeId = null, t.updatedAt = :now, t.version = t.version + 1 where t.projectId = :projectId and t.assigneeId = :userId")
    void unassignMember(UUID projectId, UUID userId, java.time.Instant now);
}
