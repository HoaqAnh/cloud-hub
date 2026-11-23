package com.cloudhub.cloud;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.cloudhub.provider.ProviderType;

public interface CloudAccountRepository extends JpaRepository<CloudAccount, UUID> {
    List<CloudAccount> findByUserId(UUID userId);
    Optional<CloudAccount> findByProviderAndExternalAccountId(ProviderType provider, String externalAccountId);
}
