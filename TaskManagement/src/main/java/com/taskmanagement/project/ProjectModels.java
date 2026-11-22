package com.taskmanagement.project;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public final class ProjectModels {
    private ProjectModels() {
    }

    public record ProjectRequest(@NotBlank @Size(max = 150) String name,
                                 @Size(max = 5000) String description) {
    }

    public record JoinRequest(@NotBlank @Size(min = 43, max = 43) String inviteCode) {
    }

    public record MemberRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record ProjectView(UUID id, String name, String description, UUID ownerId, Instant createdAt) {
        static ProjectView of(Project project) {
            return new ProjectView(project.id, project.name, project.description, project.ownerId, project.createdAt);
        }
    }

    public record InviteView(String inviteCode) {
    }

    public record MemberView(UUID userId, String name, String role, Instant joinedAt) {
    }
}
