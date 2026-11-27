package com.cloudhub.cloud;

import com.cloudhub.provider.GoogleApiException;
import com.cloudhub.provider.ProviderRegistry;
import com.cloudhub.provider.TokenSet;
import com.cloudhub.security.TokenCipher;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccessTokenService {
    private final CloudAccountRepository accounts;
    private final ProviderRegistry providers;
    private final TokenCipher cipher;
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;
    private final ReentrantLock[] refreshLocks = IntStream.range(0, 64)
        .mapToObj(index -> new ReentrantLock()).toArray(ReentrantLock[]::new);

    public AccessTokenService(CloudAccountRepository accounts, ProviderRegistry providers, TokenCipher cipher,
                              PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.providers = providers;
        this.cipher = cipher;
        this.writes = new TransactionTemplate(transactionManager);
        this.writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reads = new TransactionTemplate(transactionManager);
        this.reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.reads.setReadOnly(true);
    }

    public String accessToken(UUID accountId) {
        CloudAccount observed = account(accountId);
        if (isFresh(observed)) return decrypt(observed.getAccessTokenEnc());

        ReentrantLock lock = refreshLocks[Math.floorMod(accountId.hashCode(), refreshLocks.length)];
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider request interrupted", exception);
        }

        try {
            CloudAccount current = account(accountId);
            if (isFresh(current)) return decrypt(current.getAccessTokenEnc());
            if (!"ACTIVE".equals(current.getStatus())) throw reconnect();
            if (current.getRefreshTokenEnc() == null) {
                return markReconnect(current);
            }

            TokenSet refreshed;
            try {
                refreshed = providers.get(current.getProvider()).refresh(decrypt(current.getRefreshTokenEnc()));
            } catch (GoogleApiException exception) {
                if (exception.statusCode() != 401 && exception.statusCode() != 400) throw exception;
                return markReconnect(current);
            }

            CloudAccount saved = store(current, refreshed);
            if (!"ACTIVE".equals(saved.getStatus())) throw reconnect();
            return decrypt(saved.getAccessTokenEnc());
        } finally {
            lock.unlock();
        }
    }

    private String markReconnect(CloudAccount observed) {
        CloudAccount saved = store(observed, null);
        if (isFresh(saved)) return decrypt(saved.getAccessTokenEnc());
        throw reconnect();
    }

    private CloudAccount store(CloudAccount observed, TokenSet refreshed) {
        return writes.execute(status -> {
            CloudAccount current = accounts.lockById(observed.getId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
            if (!Objects.equals(current.getAccessTokenEnc(), observed.getAccessTokenEnc())
                || !Objects.equals(current.getRefreshTokenEnc(), observed.getRefreshTokenEnc())
                || !Objects.equals(current.getStatus(), observed.getStatus())) {
                return current;
            }
            if (refreshed == null) {
                current.setStatus("NEEDS_RECONNECT");
            } else {
                current.setTokens(cipher.encrypt(refreshed.accessToken()), cipher.encrypt(refreshed.refreshToken()),
                    refreshed.expiresAt());
            }
            return current;
        });
    }

    private CloudAccount account(UUID accountId) {
        return reads.execute(status -> accounts.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found")));
    }

    private boolean isFresh(CloudAccount account) {
        return "ACTIVE".equals(account.getStatus()) && account.getAccessTokenEnc() != null
            && account.getExpiresAt() != null && account.getExpiresAt().isAfter(Instant.now().plusSeconds(60));
    }

    private String decrypt(String token) {
        try {
            return cipher.decrypt(token);
        } catch (IllegalStateException exception) {
            throw reconnect();
        }
    }

    private static GoogleApiException reconnect() {
        return new GoogleApiException(401, "Account needs reconnect");
    }
}
