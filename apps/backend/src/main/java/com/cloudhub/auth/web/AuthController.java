package com.cloudhub.auth.web;

import com.cloudhub.auth.application.AuthService;
import com.cloudhub.security.SessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        var user = auth.authenticate(request.username(), request.password())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        HttpSession session = servletRequest.getSession();
        servletRequest.changeSessionId();
        session.setAttribute(SessionIdentity.SESSION_USER, user.id().toString());
        session.setMaxInactiveInterval(24 * 60 * 60);
        return Map.of("authenticated", true, "username", user.username());
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpSession session) {
        session.invalidate();
        return Map.of("authenticated", false);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        return auth.findIdentity(SessionIdentity.userId(session))
            .map(user -> Map.<String, Object>of("authenticated", true, "username", user.username()))
            .orElse(Map.of("authenticated", false));
    }

    public record LoginRequest(String username, String password) {}
}
