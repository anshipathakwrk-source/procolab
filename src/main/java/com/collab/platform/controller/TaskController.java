package com.collab.platform.controller;

import com.collab.platform.dto.Dtos.*;
import com.collab.platform.entity.User;
import com.collab.platform.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {
    private final TaskService service;

    @GetMapping("/projects/{pid}/tasks")
    public List<TaskRes> list(@AuthenticationPrincipal User u, @PathVariable Long pid) { return service.list(pid, u); }

    @PostMapping("/projects/{pid}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskRes create(@AuthenticationPrincipal User u, @PathVariable Long pid, @Valid @RequestBody TaskReq r) {
        return service.create(pid, u, r);
    }

    @GetMapping("/tasks/mine")
    public List<TaskRes> mine(@AuthenticationPrincipal User u) { return service.mine(u); }

    @PutMapping("/tasks/{id}")
    public TaskRes update(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody TaskReq r) {
        return service.update(id, u, r);
    }

    @PatchMapping("/tasks/{id}/status")
    public TaskRes status(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody StatusReq r) {
        return service.setStatus(id, u, r.status());
    }

    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User u, @PathVariable Long id) { service.delete(id, u); }

    @GetMapping("/tasks/{id}/comments")
    public List<CommentRes> comments(@AuthenticationPrincipal User u, @PathVariable Long id) { return service.comments(id, u); }

    @PostMapping("/tasks/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentRes addComment(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody CommentReq r) {
        return service.addComment(id, u, r);
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal User u, @PathVariable Long id) { service.deleteComment(id, u); }
}
