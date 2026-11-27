package com.cloudhub.cloud.oauth.persistence;

import com.cloudhub.auth.persistence.User;
import com.cloudhub.provider.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "oauth_states")
public class OAuthState {
    @Id
    private String state;

    @Column(name = "code_verifier_enc")
    private String codeVerifierEnc;

    @Enumerated(EnumType.STRING)
    private ProviderType provider;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected OAuthState() {}

    public OAuthState(String state, String codeVerifier, ProviderType provider, User user) {
        this.state = state;
        this.codeVerifierEnc = codeVerifier;
        this.provider = provider;
        this.user = user;
        this.expiresAt = Instant.now().plusSeconds(600);
    }

    public String getState() { return state; }
    public String getCodeVerifierEnc() { return codeVerifierEnc; }
    public ProviderType getProvider() { return provider; }
    public User getUser() { return user; }
    public Instant getExpiresAt() { return expiresAt; }
}
