package com.taskmanagement.project;

import com.taskmanagement.common.PageView;
import com.taskmanagement.common.Paging;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;

import static com.taskmanagement.project.ProjectModels.*;
import static com.taskmanagement.authentication.CurrentUser.userId;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projects;

    ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @PostMapping
    ResponseEntity<ProjectView> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProjectRequest request) {
        ProjectView project = projects.create(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/projects/" + project.id())).body(project);
    }

    @GetMapping
    PageView<ProjectView> list(@AuthenticationPrincipal Jwt jwt,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size,
                               @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return projects.list(userId(jwt), Paging.of(page, size, sort, Set.of("createdAt", "name")));
    }

    @GetMapping("/{projectId}")
    ProjectView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return projects.get(projectId, userId(jwt));
    }

    @PutMapping("/{projectId}")
    ProjectView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody ProjectRequest request) {
        return projects.update(projectId, userId(jwt), request);
    }

    @DeleteMapping("/{projectId}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        projects.delete(projectId, userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/join")
    ProjectView join(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JoinRequest request) {
        return projects.join(userId(jwt), request);
    }

    @GetMapping("/{projectId}/invitation")
    InviteView invitation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return projects.invitation(projectId, userId(jwt), false);
    }

    @PostMapping("/{projectId}/invitation")
    InviteView rotateInvitation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return projects.invitation(projectId, userId(jwt), true);
    }

    @GetMapping("/{projectId}/members")
    PageView<MemberView> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return projects.members(projectId, userId(jwt), Paging.of(page, size, "joinedAt,asc", Set.of("joinedAt")));
    }

    @PostMapping("/{projectId}/members")
    ResponseEntity<MemberView> addMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId,
                                         @Valid @RequestBody MemberRequest request) {
        MemberView member = projects.addMember(projectId, userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/members/" + member.userId())).body(member);
    }

    @DeleteMapping("/{projectId}/members/{memberId}")
    ResponseEntity<Void> removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @PathVariable UUID memberId) {
        projects.removeMember(projectId, userId(jwt), memberId);
        return ResponseEntity.noContent().build();
    }
}
