package com.collab.platform.repository;

import com.collab.platform.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findTop20ByProjectIdOrderByCreatedAtDesc(Long projectId);
}
