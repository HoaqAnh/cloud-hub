package com.cloudhub.auth;

import jakarta.servlet.http.HttpSession;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String SESSION_USER = "CLOUDHUB_USER";

    private final UserRepository users;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository users) {
        this.users = users;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request, HttpSession session) {
        User user = users.findByUsername(request.username().trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        session.setAttribute(SESSION_USER, user.getId().toString());
        session.setMaxInactiveInterval(24 * 60 * 60);
        return Map.of("authenticated", true, "username", user.getUsername());
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpSession session) {
        session.invalidate();
        return Map.of("authenticated", false);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        Object userId = session.getAttribute(SESSION_USER);
        if (userId == null) {
            return Map.of("authenticated", false);
        }
        return users.findById(java.util.UUID.fromString(userId.toString()))
            .map(user -> Map.<String, Object>of("authenticated", true, "username", user.getUsername()))
            .orElse(Map.of("authenticated", false));
    }

    public record LoginRequest(String username, String password) {}
}
