package com.collab.platform.service;

import com.collab.platform.entity.*;
import com.collab.platform.exception.ApiException;
import com.collab.platform.repository.ProjectMemberRepository;
import com.collab.platform.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Central place for "who is allowed to do what" checks. */
@Service
@RequiredArgsConstructor
public class AccessService {
    private final ProjectMemberRepository members;
    private final ProjectRepository projects;

    public Project project(Long id) {
        return projects.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    public ProjectMember member(Long projectId, Long userId) {
        return members.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "You are not a member of this project"));
    }

    public ProjectMember manager(Long projectId, Long userId) {
        ProjectMember m = member(projectId, userId);
        if (!isManager(m)) throw new ApiException(HttpStatus.FORBIDDEN, "Only owners and managers can do this");
        return m;
    }

    public boolean isManager(ProjectMember m) {
        return m.getRole() == ProjectRole.OWNER || m.getRole() == ProjectRole.MANAGER;
    }
}
