package com.taskmanagement.project;

import com.taskmanagement.common.ApiException;
import com.taskmanagement.common.PageView;
import com.taskmanagement.common.Text;
import com.taskmanagement.task.TaskRepository;
import com.taskmanagement.authentication.AppUser;
import com.taskmanagement.authentication.AuthService;
import com.taskmanagement.authentication.UserRepository;
import com.taskmanagement.authorization.ProjectAccess;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.taskmanagement.project.ProjectModels.*;

@Service
@Transactional
public class ProjectService {
    private final ProjectRepository projects;
    private final MemberRepository members;
    private final UserRepository users;
    private final TaskRepository tasks;
    private final ProjectAccess access;
    private final SecureRandom random = new SecureRandom();

    ProjectService(ProjectRepository projects, MemberRepository members, UserRepository users,
                   TaskRepository tasks, ProjectAccess access) {
        this.projects = projects;
        this.members = members;
        this.users = users;
        this.tasks = tasks;
        this.access = access;
    }

    ProjectView create(UUID userId, ProjectRequest request) {
        Project project = projects.saveAndFlush(new Project(request.name().strip(), Text.normalize(request.description()), userId, inviteCode()));
        members.save(new ProjectMember(project.id, userId));
        return ProjectView.of(project);
    }

    @Transactional(readOnly = true)
    PageView<ProjectView> list(UUID userId, Pageable page) {
        return PageView.of(projects.findForUser(userId, page).map(ProjectView::of));
    }

    @Transactional(readOnly = true)
    ProjectView get(UUID projectId, UUID userId) {
        return ProjectView.of(access.member(projectId, userId, false));
    }

    ProjectView update(UUID projectId, UUID userId, ProjectRequest request) {
        Project project = access.owner(projectId, userId);
        project.name = request.name().strip();
        project.description = Text.normalize(request.description());
        return ProjectView.of(project);
    }

    void delete(UUID projectId, UUID userId) {
        projects.delete(access.owner(projectId, userId));
    }

    ProjectView join(UUID userId, JoinRequest request) {
        Project project = projects.findByInviteCode(request.inviteCode()).orElseThrow(() -> ApiException.notFound("Invitation"));
        if (!members.existsByProjectIdAndUserId(project.id, userId)) {
            members.save(new ProjectMember(project.id, userId));
        }
        return ProjectView.of(project);
    }

    InviteView invitation(UUID projectId, UUID userId, boolean rotate) {
        Project project = access.owner(projectId, userId);
        if (rotate) {
            project.inviteCode = inviteCode();
        }
        return new InviteView(project.inviteCode);
    }

    @Transactional(readOnly = true)
    PageView<MemberView> members(UUID projectId, UUID userId, Pageable pageable) {
        Project project = access.member(projectId, userId, false);
        var page = members.findByProjectId(projectId, pageable);
        var accounts = users.findAllById(page.stream().map(m -> m.userId).toList()).stream()
            .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return PageView.of(page.map(m -> new MemberView(m.userId, accounts.get(m.userId).getName(),
            project.ownerId.equals(m.userId) ? "OWNER" : "MEMBER", m.joinedAt)));
    }

    MemberView addMember(UUID projectId, UUID userId, MemberRequest request) {
        Project project = access.owner(projectId, userId);
        AppUser account = users.findByEmail(AuthService.normalizeEmail(request.email())).orElseThrow(() -> ApiException.notFound("User"));
        if (members.existsByProjectIdAndUserId(projectId, account.getId())) {
            throw ApiException.conflict("User is already a project member");
        }
        ProjectMember member = members.save(new ProjectMember(projectId, account.getId()));
        return new MemberView(account.getId(), account.getName(), project.ownerId.equals(account.getId()) ? "OWNER" : "MEMBER", member.joinedAt);
    }

    void removeMember(UUID projectId, UUID requesterId, UUID memberId) {
        access.removableMember(projectId, requesterId, memberId);
        ProjectMember member = members.findByProjectIdAndUserId(projectId, memberId).orElseThrow(() -> ApiException.notFound("Member"));
        tasks.unassignMember(projectId, memberId, java.time.Instant.now());
        members.delete(member);
    }

    private String inviteCode() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
