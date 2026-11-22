package com.taskmanagement.task;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class TaskModels {
    private TaskModels() {
    }

    public record TaskRequest(@NotBlank @Size(max = 200) String title,
                              @Size(max = 10000) String description,
                              @NotNull Task.Status status,
                              @NotNull Task.Priority priority,
                              LocalDate dueDate, UUID assigneeId) {
    }

    public record TaskUpdateRequest(@NotBlank @Size(max = 200) String title,
                                    @Size(max = 10000) String description,
                                    @NotNull Task.Status status,
                                    @NotNull Task.Priority priority,
                                    LocalDate dueDate, UUID assigneeId,
                                    @NotNull @PositiveOrZero Long version) {
    }

    public record TaskView(UUID id, UUID projectId, String title, String description, Task.Status status,
                           Task.Priority priority, LocalDate dueDate, UUID creatorId, UUID assigneeId,
                           Instant createdAt, Instant updatedAt, long version) {
        static TaskView of(Task task) {
            return new TaskView(task.id, task.projectId, task.title, task.description, task.status, task.priority,
                task.dueDate, task.creatorId, task.assigneeId, task.createdAt, task.updatedAt, task.version);
        }
    }
}
