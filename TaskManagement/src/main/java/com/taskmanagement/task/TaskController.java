package com.taskmanagement.task;

import com.taskmanagement.common.PageView;
import com.taskmanagement.common.Paging;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;

import static com.taskmanagement.task.TaskModels.*;
import static com.taskmanagement.authentication.CurrentUser.userId;

@RestController
@RequestMapping("/api")
public class TaskController {
    private final TaskService tasks;

    TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @PostMapping("/projects/{projectId}/tasks")
    ResponseEntity<TaskView> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody TaskRequest request) {
        TaskView task = tasks.create(projectId, userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/tasks/" + task.id())).body(task);
    }

    @GetMapping("/tasks")
    PageView<TaskView> list(@AuthenticationPrincipal Jwt jwt,
                            @RequestParam(required = false) UUID projectId,
                            @RequestParam(required = false) Task.Status status,
                            @RequestParam(required = false) Task.Priority priority,
                            @RequestParam(required = false) UUID assigneeId,
                            @RequestParam(required = false) LocalDate dueFrom,
                            @RequestParam(required = false) LocalDate dueTo,
                            @RequestParam(required = false) String q,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size,
                            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return tasks.list(userId(jwt), projectId, status, priority, assigneeId, dueFrom, dueTo, q,
            Paging.of(page, size, sort, Set.of("createdAt", "updatedAt", "title", "dueDate")));
    }

    @GetMapping("/tasks/{taskId}")
    TaskView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId) {
        return tasks.get(taskId, userId(jwt));
    }

    @PutMapping("/tasks/{taskId}")
    TaskView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId, @Valid @RequestBody TaskUpdateRequest request) {
        return tasks.update(taskId, userId(jwt), request);
    }

    @DeleteMapping("/tasks/{taskId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID taskId) {
        tasks.delete(taskId, userId(jwt));
        return ResponseEntity.noContent().build();
    }
}
