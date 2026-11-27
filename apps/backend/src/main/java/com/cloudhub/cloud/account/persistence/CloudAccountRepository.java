package com.cloudhub.cloud.account.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import com.cloudhub.provider.ProviderType;

public interface CloudAccountRepository extends JpaRepository<CloudAccount, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from CloudAccount account where account.id = :id")
    Optional<CloudAccount> lockById(UUID id);

    List<CloudAccount> findByUserId(UUID userId);
    Optional<CloudAccount> findByProviderAndExternalAccountId(ProviderType provider, String externalAccountId);
}
