package com.cloudhub.cloud.oauth.persistence;

import com.cloudhub.provider.ProviderType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OAuthStateRepository extends JpaRepository<OAuthState, String> {
    @Modifying
    @Query("""
        delete from OAuthState s
        where s.state = :state and s.provider = :provider and s.user.id = :userId and s.expiresAt > :now
        """)
    int consume(String state, ProviderType provider, UUID userId, Instant now);
}
