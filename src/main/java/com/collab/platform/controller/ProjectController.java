package com.collab.platform.controller;

import com.collab.platform.dto.Dtos.*;
import com.collab.platform.entity.User;
import com.collab.platform.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService service;

    @GetMapping
    public List<ProjectRes> list(@AuthenticationPrincipal User u) { return service.list(u); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectRes create(@AuthenticationPrincipal User u, @Valid @RequestBody ProjectReq r) { return service.create(u, r); }

    @GetMapping("/{id}")
    public ProjectRes get(@AuthenticationPrincipal User u, @PathVariable Long id) { return service.get(id, u); }

    @PutMapping("/{id}")
    public ProjectRes update(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody ProjectReq r) {
        return service.update(id, u, r);
    }

    @PatchMapping("/{id}/archive")
    public ProjectRes archive(@AuthenticationPrincipal User u, @PathVariable Long id) { return service.toggleArchive(id, u); }

    @GetMapping("/{id}/members")
    public List<MemberRes> members(@AuthenticationPrincipal User u, @PathVariable Long id) { return service.members(id, u); }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberRes addMember(@AuthenticationPrincipal User u, @PathVariable Long id, @Valid @RequestBody MemberReq r) {
        return service.addMember(id, u, r);
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal User u, @PathVariable Long id, @PathVariable Long memberId) {
        service.removeMember(id, memberId, u);
    }

    @GetMapping("/{id}/progress")
    public ProgressRes progress(@AuthenticationPrincipal User u, @PathVariable Long id) { return service.progress(id, u); }

    @GetMapping("/{id}/activity")
    public List<ActivityRes> activity(@AuthenticationPrincipal User u, @PathVariable Long id) { return service.activity(id, u); }
}
