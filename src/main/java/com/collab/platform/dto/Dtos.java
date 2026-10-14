package com.collab.platform.dto;

import com.collab.platform.entity.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** All request/response shapes in one place. Entities are never returned directly. */
public final class Dtos {
    private Dtos() {}

    // ----- auth -----
    public record RegisterReq(
            @NotBlank(message = "Name is required") String name,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") String email,
            @NotBlank(message = "Password is required")
            @Size(min = 6, message = "Password must be at least 6 characters") String password) {}

    public record LoginReq(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password) {}

    public record UserDto(Long id, String name, String email) {}
    public record AuthRes(String token, UserDto user) {}

    // ----- projects -----
    public record ProjectReq(
            @NotBlank(message = "Project title is required") String title,
            String description, LocalDate startDate, LocalDate endDate) {}

    public record ProjectRes(Long id, String title, String description, LocalDate startDate, LocalDate endDate,
                             ProjectStatus status, String ownerName, ProjectRole myRole,
                             long memberCount, long taskCount, int progress) {}

    public record MemberReq(
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") String email,
            ProjectRole role) {}

    public record MemberRes(Long id, Long userId, String name, String email, ProjectRole role) {}

    public record ProgressRes(long total, long todo, long inProgress, long done, long overdue, int percent) {}

    public record ActivityRes(String userName, String action, LocalDateTime createdAt) {}

    // ----- tasks -----
    public record TaskReq(
            @NotBlank(message = "Task title is required") String title,
            String description, Long assigneeId, Priority priority, LocalDate dueDate) {}

    public record StatusReq(@NotNull(message = "Status is required") TaskStatus status) {}

    public record TaskRes(Long id, Long projectId, String projectTitle, String title, String description,
                          Long assigneeId, String assigneeName, Priority priority, TaskStatus status,
                          LocalDate dueDate, boolean overdue, long commentCount) {}

    // ----- comments -----
    public record CommentReq(@NotBlank(message = "Comment cannot be empty") String content, Long parentId) {}

    public record CommentRes(Long id, Long taskId, Long userId, String userName, String content,
                             Long parentId, LocalDateTime createdAt) {}

    // ----- files -----
    public record FileRes(Long id, String fileName, long size, String uploadedByName,
                          Long taskId, String taskTitle, LocalDateTime uploadedAt) {}
}
