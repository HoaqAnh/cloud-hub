package com.cloudhub.auth.application;

import com.cloudhub.auth.persistence.User;
import com.cloudhub.auth.persistence.UserRepository;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final String username;
    private final String password;

    public AdminUserInitializer(
        UserRepository users,
        @Value("${cloudhub.admin.username}") String username,
        @Value("${cloudhub.admin.password}") String password
    ) {
        this.users = users;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) throw new IllegalStateException("Set CLOUDHUB_ADMIN_PASSWORD before starting CloudHub");
        var encoder = new BCryptPasswordEncoder();
        var existing = users.findByUsername(username);
        if (existing.isEmpty()) {
            users.save(new User(UUID.randomUUID(), username, encoder.encode(password)));
        } else {
            User admin = existing.get();
            if (!encoder.matches(password, admin.getPasswordHash())) {
                admin.changePasswordHash(encoder.encode(password));
                users.save(admin);
            }
        }
    }
}
