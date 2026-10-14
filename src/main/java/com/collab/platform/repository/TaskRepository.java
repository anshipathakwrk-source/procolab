package com.collab.platform.repository;

import com.collab.platform.entity.Task;
import com.collab.platform.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<Task> findByAssigneeIdAndStatusNot(Long assigneeId, TaskStatus status);
    long countByProjectId(Long projectId);
    long countByProjectIdAndStatus(Long projectId, TaskStatus status);
    long countByProjectIdAndStatusNotAndDueDateBefore(Long projectId, TaskStatus status, LocalDate date);
}
