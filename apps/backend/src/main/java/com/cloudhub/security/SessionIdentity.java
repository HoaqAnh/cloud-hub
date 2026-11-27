package com.cloudhub.security;

import com.cloudhub.auth.application.AuthService;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SessionIdentity {
    public static final String SESSION_USER = "CLOUDHUB_USER";

    private final AuthService auth;

    public SessionIdentity(AuthService auth) {
        this.auth = auth;
    }

    public UUID requireUserId(HttpSession session) {
        return auth.findIdentity(userId(session))
            .orElseThrow(() -> new IllegalArgumentException("Login required"))
            .id();
    }

    public static UUID userId(HttpSession session) {
        Object id = session.getAttribute(SESSION_USER);
        return id == null ? null : UUID.fromString(id.toString());
    }
}
