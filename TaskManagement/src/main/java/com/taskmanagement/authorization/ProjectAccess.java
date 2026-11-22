package com.taskmanagement.authorization;

import com.taskmanagement.common.ApiException;
import com.taskmanagement.project.MemberRepository;
import com.taskmanagement.project.Project;
import com.taskmanagement.project.ProjectRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Centralizes access checks; callers supply the transaction for project mutations.
 */
@Component
public class ProjectAccess {
    private final ProjectRepository projects;
    private final MemberRepository members;

    ProjectAccess(ProjectRepository projects, MemberRepository members) {
        this.projects = projects;
        this.members = members;
    }

    public Project member(UUID projectId, UUID userId, boolean lock) {
        // Mutations lock the project so membership changes and assignments cannot race.
        Project project = (lock ? projects.findLockedById(projectId) : projects.findById(projectId))
            .orElseThrow(() -> ApiException.notFound("Project"));
        if (!members.existsByProjectIdAndUserId(projectId, userId)) {
            throw ApiException.notFound("Project");
        }
        return project;
    }

    public Project owner(UUID projectId, UUID userId) {
        Project project = member(projectId, userId, true);
        if (!project.getOwnerId().equals(userId)) {
            throw ApiException.forbidden("Only the project owner can perform this action");
        }
        return project;
    }

    public void assignee(UUID projectId, UUID userId) {
        if (userId != null && !members.existsByProjectIdAndUserId(projectId, userId)) {
            throw ApiException.badRequest("Assignee must be a member of the project");
        }
    }

    public void removableMember(UUID projectId, UUID requesterId, UUID memberId) {
        Project project = member(projectId, requesterId, true);
        if (!requesterId.equals(memberId) && !project.getOwnerId().equals(requesterId)) {
            throw ApiException.forbidden("Only the owner may remove another member");
        }
        if (project.getOwnerId().equals(memberId)) {
            throw ApiException.conflict("The project owner cannot leave or be removed");
        }
    }

    public void author(UUID authorId, UUID userId) {
        if (!authorId.equals(userId)) {
            throw ApiException.forbidden("Only the author can edit a comment");
        }
    }

    public void authorOrOwner(UUID projectId, UUID authorId, UUID userId, String message) {
        if (!authorId.equals(userId) && !isOwner(projectId, userId)) {
            throw ApiException.forbidden(message);
        }
    }

    private boolean isOwner(UUID projectId, UUID userId) {
        return projects.findById(projectId).map(project -> project.getOwnerId().equals(userId)).orElse(false);
    }
}
