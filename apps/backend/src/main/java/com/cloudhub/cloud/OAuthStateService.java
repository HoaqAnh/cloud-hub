package com.cloudhub.cloud;

import com.cloudhub.provider.ProviderType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthStateService {
    private final OAuthStateRepository states;

    public OAuthStateService(OAuthStateRepository states) {
        this.states = states;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String consume(String state, ProviderType provider, UUID userId) {
        Instant now = Instant.now();
        OAuthState oauth = states.findById(state)
            .filter(candidate -> candidate.getProvider() == provider)
            .filter(candidate -> candidate.getUser().getId().equals(userId))
            .filter(candidate -> candidate.getExpiresAt().isAfter(now))
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired OAuth state"));
        if (states.consume(state, provider, userId, now) != 1) {
            throw new IllegalArgumentException("Invalid or expired OAuth state");
        }
        return oauth.getCodeVerifierEnc();
    }
}
