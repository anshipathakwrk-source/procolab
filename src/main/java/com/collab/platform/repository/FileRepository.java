package com.collab.platform.repository;

import com.collab.platform.entity.FileAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileRepository extends JpaRepository<FileAttachment, Long> {
    List<FileAttachment> findByProjectIdOrderByUploadedAtDesc(Long projectId);
    List<FileAttachment> findByTaskId(Long taskId);
}
