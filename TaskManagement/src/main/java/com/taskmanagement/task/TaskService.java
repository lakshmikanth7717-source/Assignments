package com.taskmanagement.task;

import com.taskmanagement.common.ApiException;
import com.taskmanagement.common.PageView;
import com.taskmanagement.common.Text;
import com.taskmanagement.project.ProjectMember;
import com.taskmanagement.authorization.ProjectAccess;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static com.taskmanagement.task.TaskModels.*;

@Service
@Transactional
public class TaskService {
    private final TaskRepository tasks;
    private final ProjectAccess access;

    TaskService(TaskRepository tasks, ProjectAccess access) {
        this.tasks = tasks;
        this.access = access;
    }

    TaskView create(UUID projectId, UUID userId, TaskRequest request) {
        access.member(projectId, userId, true);
        access.assignee(projectId, request.assigneeId());
        Task task = new Task(projectId, userId);
        apply(task, request.title(), request.description(), request.status(), request.priority(), request.dueDate(), request.assigneeId());
        return TaskView.of(tasks.saveAndFlush(task));
    }

    @Transactional(readOnly = true)
    TaskView get(UUID taskId, UUID userId) {
        return TaskView.of(accessible(taskId, userId, false));
    }

    TaskView update(UUID taskId, UUID userId, TaskUpdateRequest request) {
        Task task = accessible(taskId, userId, true);
        if (task.version != request.version()) {
            throw ApiException.conflict("Task changed; reload it before updating");
        }
        access.assignee(task.projectId, request.assigneeId());
        apply(task, request.title(), request.description(), request.status(), request.priority(), request.dueDate(), request.assigneeId());
        return TaskView.of(tasks.saveAndFlush(task));
    }

    void delete(UUID taskId, UUID userId) {
        tasks.delete(accessible(taskId, userId, true));
    }

    /**
     * Authorizes access before loading the task; writes lock the project against membership changes.
     */
    public Task accessible(UUID taskId, UUID userId, boolean lock) {
        UUID projectId = tasks.findProjectIdById(taskId).orElseThrow(() -> ApiException.notFound("Task"));
        access.member(projectId, userId, lock);
        return tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("Task"));
    }

    @Transactional(readOnly = true)
    PageView<TaskView> list(UUID userId, UUID projectId, Task.Status status, Task.Priority priority,
                            UUID assigneeId, LocalDate dueFrom, LocalDate dueTo, String q, Pageable pageable) {
        if (projectId != null) {
            access.member(projectId, userId, false);
        }
        if (q != null && q.length() > 200) {
            throw ApiException.badRequest("Search query must not exceed 200 characters");
        }
        if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
            throw ApiException.badRequest("dueFrom must not be after dueTo");
        }
        Specification<Task> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            var memberships = query.subquery(UUID.class);
            var membership = memberships.from(ProjectMember.class);
            memberships.select(membership.get("projectId")).where(cb.equal(membership.get("userId"), userId));
            predicates.add(root.get("projectId").in(memberships));
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (assigneeId != null) {
                predicates.add(cb.equal(root.get("assigneeId"), assigneeId));
            }
            if (dueFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), dueFrom));
            }
            if (dueTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), dueTo));
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'), cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageView.of(tasks.findAll(specification, pageable).map(TaskView::of));
    }

    private void apply(Task task, String title, String description, Task.Status status, Task.Priority priority, LocalDate dueDate, UUID assigneeId) {
        task.title = title.strip();
        task.description = Text.normalize(description);
        task.status = status;
        task.priority = priority;
        task.dueDate = dueDate;
        task.assigneeId = assigneeId;
    }
}
