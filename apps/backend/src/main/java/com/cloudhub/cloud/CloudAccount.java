package com.cloudhub.cloud;

import com.cloudhub.auth.User;
import com.cloudhub.provider.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cloud_accounts", uniqueConstraints =
    @UniqueConstraint(columnNames = {"provider", "external_account_id"}))
public class CloudAccount {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private ProviderType provider;

    @Column(name = "external_account_id", nullable = false)
    private String externalAccountId;

    @Column(name = "display_name")
    private String displayName;

    private String email;

    @Column(name = "access_token_enc")
    private String accessTokenEnc;

    @Column(name = "refresh_token_enc")
    private String refreshTokenEnc;

    @Column(name = "expires_at")
    private Instant expiresAt;

    private String scopes;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CloudAccount() {}

    public CloudAccount(User user, ProviderType provider, String externalAccountId) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider;
        this.externalAccountId = externalAccountId;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public ProviderType getProvider() { return provider; }
    public String getExternalAccountId() { return externalAccountId; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getAccessTokenEnc() { return accessTokenEnc; }
    public String getRefreshTokenEnc() { return refreshTokenEnc; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getScopes() { return scopes; }
    public String getStatus() { return status; }

    public void setDetails(String displayName, String email, String scopes) {
        this.displayName = displayName;
        this.email = email;
        this.scopes = scopes;
        this.updatedAt = Instant.now();
    }
    public void setTokens(String accessTokenEnc, String refreshTokenEnc, Instant expiresAt) {
        this.accessTokenEnc = accessTokenEnc;
        if (refreshTokenEnc != null) this.refreshTokenEnc = refreshTokenEnc;
        this.expiresAt = expiresAt;
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
    }
}
