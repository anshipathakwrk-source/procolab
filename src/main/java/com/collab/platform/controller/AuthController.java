package com.collab.platform.controller;

import com.collab.platform.dto.Dtos.*;
import com.collab.platform.entity.User;
import com.collab.platform.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthRes register(@Valid @RequestBody RegisterReq r) { return auth.register(r); }

    @PostMapping("/login")
    public AuthRes login(@Valid @RequestBody LoginReq r) { return auth.login(r); }
}
