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
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository tasks;
    private final CommentRepository comments;
    private final FileRepository files;
    private final ProjectMemberRepository members;
    private final AccessService access;
    private final ActivityService activity;

    // ---------- tasks ----------
    @Transactional(readOnly = true)
    public List<TaskRes> list(Long projectId, User user) {
        access.member(projectId, user.getId());
        return tasks.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(this::toTaskRes).toList();
    }

    /** Open tasks assigned to the current user across all projects, soonest due first. */
    @Transactional(readOnly = true)
    public List<TaskRes> mine(User user) {
        return tasks.findByAssigneeIdAndStatusNot(user.getId(), TaskStatus.DONE).stream()
                .map(this::toTaskRes)
                .sorted(Comparator.comparing(TaskRes::dueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional
    public TaskRes create(Long projectId, User user, TaskReq r) {
        access.member(projectId, user.getId());
        Project p = access.project(projectId);
        Task t = new Task();
        t.setProject(p);
        t.setCreatedBy(user);
        t.setStatus(TaskStatus.TODO);
        apply(t, r, projectId);
        tasks.save(t);
        activity.log(p, user, "created task \"" + t.getTitle() + "\"");
        return toTaskRes(t);
    }

    @Transactional
    public TaskRes update(Long id, User user, TaskReq r) {
        Task t = load(id);
        Long pid = t.getProject().getId();
        ProjectMember m = access.member(pid, user.getId());
        boolean allowed = access.isManager(m)
                || t.getCreatedBy().getId().equals(user.getId())
                || (t.getAssignee() != null && t.getAssignee().getId().equals(user.getId()));
        if (!allowed) throw new ApiException(HttpStatus.FORBIDDEN, "Only managers, the creator or the assignee can edit this task");
        apply(t, r, pid);
        activity.log(t.getProject(), user, "updated task \"" + t.getTitle() + "\"");
        return toTaskRes(t);
    }

    @Transactional
    public TaskRes setStatus(Long id, User user, TaskStatus status) {
        Task t = load(id);
        access.member(t.getProject().getId(), user.getId());
        if (t.getStatus() != status) {
            t.setStatus(status);
            activity.log(t.getProject(), user, "moved \"" + t.getTitle() + "\" to " + label(status));
        }
        return toTaskRes(t);
    }

    @Transactional
    public void delete(Long id, User user) {
        Task t = load(id);
        ProjectMember m = access.member(t.getProject().getId(), user.getId());
        if (!access.isManager(m) && !t.getCreatedBy().getId().equals(user.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only managers or the task creator can delete a task");
        files.findByTaskId(id).forEach(f -> f.setTask(null));   // keep files, just unlink them
        activity.log(t.getProject(), user, "deleted task \"" + t.getTitle() + "\"");
        tasks.delete(t);                                          // comments are removed by ON DELETE CASCADE
    }

    // ---------- comments ----------
    @Transactional(readOnly = true)
    public List<CommentRes> comments(Long taskId, User user) {
        Task t = load(taskId);
        access.member(t.getProject().getId(), user.getId());
        return comments.findByTaskIdOrderByCreatedAtAsc(taskId).stream().map(this::toCommentRes).toList();
    }

    @Transactional
    public CommentRes addComment(Long taskId, User user, CommentReq r) {
        Task t = load(taskId);
        access.member(t.getProject().getId(), user.getId());
        Comment c = new Comment();
        c.setTask(t);
        c.setUser(user);
        c.setContent(r.content().trim());
        if (r.parentId() != null) {
            Comment parent = comments.findById(r.parentId())
                    .filter(x -> x.getTask().getId().equals(taskId))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "The comment you are replying to no longer exists"));
            c.setParent(parent);
        }
        comments.save(c);
        activity.log(t.getProject(), user, "commented on \"" + t.getTitle() + "\"");
        return toCommentRes(c);
    }

    @Transactional
    public void deleteComment(Long commentId, User user) {
        Comment c = comments.findById(commentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Comment not found"));
        ProjectMember m = access.member(c.getTask().getProject().getId(), user.getId());
        if (!access.isManager(m) && !c.getUser().getId().equals(user.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only delete your own comments");
        comments.delete(c);                                       // replies are removed by ON DELETE CASCADE
    }

    // ---------- helpers ----------
    private Task load(Long id) {
        return tasks.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private void apply(Task t, TaskReq r, Long projectId) {
        t.setTitle(r.title().trim());
        t.setDescription(r.description());
        t.setPriority(r.priority() == null ? Priority.MEDIUM : r.priority());
        t.setDueDate(r.dueDate());
        if (r.assigneeId() == null) {
            t.setAssignee(null);
        } else {
            ProjectMember m = members.findByProjectIdAndUserId(projectId, r.assigneeId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "The assignee must be a member of this project"));
            t.setAssignee(m.getUser());
        }
    }

    private String label(TaskStatus s) {
        return switch (s) {
            case TODO -> "To do";
            case IN_PROGRESS -> "In progress";
            case DONE -> "Done";
        };
    }

    private TaskRes toTaskRes(Task t) {
        boolean overdue = t.getDueDate() != null && t.getStatus() != TaskStatus.DONE
                && t.getDueDate().isBefore(LocalDate.now());
        User a = t.getAssignee();
        return new TaskRes(t.getId(), t.getProject().getId(), t.getProject().getTitle(), t.getTitle(),
                t.getDescription(), a == null ? null : a.getId(), a == null ? null : a.getName(),
                t.getPriority(), t.getStatus(), t.getDueDate(), overdue, comments.countByTaskId(t.getId()));
    }

    private CommentRes toCommentRes(Comment c) {
        return new CommentRes(c.getId(), c.getTask().getId(), c.getUser().getId(), c.getUser().getName(),
                c.getContent(), c.getParent() == null ? null : c.getParent().getId(), c.getCreatedAt());
    }
}
