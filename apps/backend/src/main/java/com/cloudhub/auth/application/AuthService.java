package com.cloudhub.auth.application;

import com.cloudhub.auth.persistence.User;
import com.cloudhub.auth.persistence.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository users) {
        this.users = users;
    }

    public Optional<Identity> authenticate(String username, String password) {
        return users.findByUsername(username.trim())
            .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
            .map(Identity::from);
    }

    public Optional<Identity> findIdentity(UUID userId) {
        return findUser(userId).map(Identity::from);
    }

    public User requireUser(UUID userId) {
        return findUser(userId).orElseThrow(() -> new IllegalArgumentException("Login required"));
    }

    private Optional<User> findUser(UUID userId) {
        return userId == null ? Optional.empty() : users.findById(userId);
    }

    public record Identity(UUID id, String username) {
        private static Identity from(User user) {
            return new Identity(user.getId(), user.getUsername());
        }
    }
}
