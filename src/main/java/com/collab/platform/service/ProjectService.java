package com.collab.platform.service;

import com.collab.platform.dto.Dtos.*;
import com.collab.platform.entity.*;
import com.collab.platform.exception.ApiException;
import com.collab.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final ActivityRepository activityRepo;
    private final AccessService access;
    private final ActivityService activity;

    @Transactional
    public ProjectRes create(User user, ProjectReq r) {
        Project p = new Project();
        apply(p, r);
        p.setOwner(user);
        p.setStatus(ProjectStatus.ACTIVE);
        projects.save(p);

        ProjectMember m = new ProjectMember();
        m.setProject(p);
        m.setUser(user);
        m.setRole(ProjectRole.OWNER);
        members.save(m);

        activity.log(p, user, "created the project");
        return toRes(p, ProjectRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<ProjectRes> list(User user) {
        return members.findByUserId(user.getId()).stream()
                .map(m -> toRes(m.getProject(), m.getRole()))
                .sorted((a, b) -> b.id().compareTo(a.id()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectRes get(Long id, User user) {
        ProjectMember m = access.member(id, user.getId());
        return toRes(m.getProject(), m.getRole());
    }

    @Transactional
    public ProjectRes update(Long id, User user, ProjectReq r) {
        ProjectMember m = access.manager(id, user.getId());
        Project p = m.getProject();
        apply(p, r);
        activity.log(p, user, "updated the project details");
        return toRes(p, m.getRole());
    }

    /** Owner-only toggle between ACTIVE and ARCHIVED. */
    @Transactional
    public ProjectRes toggleArchive(Long id, User user) {
        ProjectMember m = access.member(id, user.getId());
        if (m.getRole() != ProjectRole.OWNER)
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the project owner can archive a project");
        Project p = m.getProject();
        boolean archiving = p.getStatus() == ProjectStatus.ACTIVE;
        p.setStatus(archiving ? ProjectStatus.ARCHIVED : ProjectStatus.ACTIVE);
        activity.log(p, user, archiving ? "archived the project" : "restored the project");
        return toRes(p, m.getRole());
    }

    // ---------- members ----------
    @Transactional(readOnly = true)
    public List<MemberRes> members(Long projectId, User user) {
        access.member(projectId, user.getId());
        return members.findByProjectId(projectId).stream()
                .map(m -> new MemberRes(m.getId(), m.getUser().getId(), m.getUser().getName(),
                        m.getUser().getEmail(), m.getRole()))
                .toList();
    }

    @Transactional
    public MemberRes addMember(Long projectId, User user, MemberReq r) {
        access.manager(projectId, user.getId());
        Project p = access.project(projectId);
        User invited = users.findByEmail(r.email().trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No account found for that email. Ask them to sign up first."));
        if (members.findByProjectIdAndUserId(projectId, invited.getId()).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, invited.getName() + " is already on this project");
        ProjectRole role = r.role() == null ? ProjectRole.MEMBER : r.role();
        if (role == ProjectRole.OWNER)
            throw new ApiException(HttpStatus.BAD_REQUEST, "A project can only have one owner");

        ProjectMember m = new ProjectMember();
        m.setProject(p);
        m.setUser(invited);
        m.setRole(role);
        members.save(m);
        activity.log(p, user, "added " + invited.getName() + " as " + role.name().toLowerCase());
        return new MemberRes(m.getId(), invited.getId(), invited.getName(), invited.getEmail(), role);
    }

    @Transactional
    public void removeMember(Long projectId, Long memberId, User user) {
        access.manager(projectId, user.getId());
        ProjectMember target = members.findById(memberId)
                .filter(m -> m.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Member not found"));
        if (target.getRole() == ProjectRole.OWNER)
            throw new ApiException(HttpStatus.BAD_REQUEST, "The project owner cannot be removed");
        activity.log(target.getProject(), user, "removed " + target.getUser().getName() + " from the project");
        members.delete(target);
    }

    // ---------- progress & activity ----------
    @Transactional(readOnly = true)
    public ProgressRes progress(Long projectId, User user) {
        access.member(projectId, user.getId());
        return calcProgress(projectId);
    }

    @Transactional(readOnly = true)
    public List<ActivityRes> activity(Long projectId, User user) {
        access.member(projectId, user.getId());
        return activityRepo.findTop20ByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(a -> new ActivityRes(a.getUser().getName(), a.getAction(), a.getCreatedAt()))
                .toList();
    }

    // ---------- helpers ----------
    private ProgressRes calcProgress(Long pid) {
        long total = tasks.countByProjectId(pid);
        long todo = tasks.countByProjectIdAndStatus(pid, TaskStatus.TODO);
        long inProg = tasks.countByProjectIdAndStatus(pid, TaskStatus.IN_PROGRESS);
        long done = tasks.countByProjectIdAndStatus(pid, TaskStatus.DONE);
        long overdue = tasks.countByProjectIdAndStatusNotAndDueDateBefore(pid, TaskStatus.DONE, LocalDate.now());
        int pct = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        return new ProgressRes(total, todo, inProg, done, overdue, pct);
    }

    private void apply(Project p, ProjectReq r) {
        if (r.startDate() != null && r.endDate() != null && r.endDate().isBefore(r.startDate()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date cannot be before the start date");
        p.setTitle(r.title().trim());
        p.setDescription(r.description());
        p.setStartDate(r.startDate());
        p.setEndDate(r.endDate());
    }

    private ProjectRes toRes(Project p, ProjectRole role) {
        ProgressRes pr = calcProgress(p.getId());
        return new ProjectRes(p.getId(), p.getTitle(), p.getDescription(), p.getStartDate(), p.getEndDate(),
                p.getStatus(), p.getOwner().getName(), role,
                members.countByProjectId(p.getId()), pr.total(), pr.percent());
    }
}
