package com.cloudhub.auth;

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
        if (users.findByUsername(username).isEmpty()) {
            String hash = new BCryptPasswordEncoder().encode(password);
            users.save(new User(UUID.randomUUID(), username, hash));
        }
    }
}
