package com.collab.platform.service;

import com.collab.platform.entity.*;
import com.collab.platform.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityService {
    private final ActivityRepository repo;

    public void log(Project project, User user, String action) {
        ActivityLog a = new ActivityLog();
        a.setProject(project);
        a.setUser(user);
        a.setAction(action.length() > 300 ? action.substring(0, 300) : action);
        repo.save(a);
    }
}
