package com.collab.platform.service;

import com.collab.platform.dto.Dtos.*;
import com.collab.platform.entity.User;
import com.collab.platform.exception.ApiException;
import com.collab.platform.repository.UserRepository;
import com.collab.platform.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;

    public AuthRes register(RegisterReq r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email))
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        User u = new User();
        u.setName(r.name().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(r.password()));
        users.save(u);
        return new AuthRes(jwt.generate(email), toDto(u));
    }

    public AuthRes login(LoginReq r) {
        String email = r.email().trim().toLowerCase();
        User u = users.findByEmail(email)
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password"));
        return new AuthRes(jwt.generate(email), toDto(u));
    }

    public static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getName(), u.getEmail());
    }
}
