package com.cloudhub.cloud.account.application;

import com.cloudhub.auth.persistence.User;
import com.cloudhub.cloud.account.persistence.CloudAccount;
import com.cloudhub.cloud.account.persistence.CloudAccountRepository;
import com.cloudhub.provider.AccountInfo;
import com.cloudhub.provider.Capabilities;
import com.cloudhub.provider.ProviderRegistry;
import com.cloudhub.provider.ProviderType;
import com.cloudhub.provider.StorageQuota;
import com.cloudhub.provider.TokenSet;
import com.cloudhub.security.TokenCipher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloudAccountService {
    private final CloudAccountRepository accounts;
    private final ProviderRegistry providers;
    private final AccessTokenService tokens;
    private final TokenCipher cipher;

    public CloudAccountService(CloudAccountRepository accounts, ProviderRegistry providers,
                               AccessTokenService tokens, TokenCipher cipher) {
        this.accounts = accounts;
        this.providers = providers;
        this.tokens = tokens;
        this.cipher = cipher;
    }

    public List<AccountSummary> list(UUID userId) {
        return accounts.findByUserId(userId).stream()
            .map(account -> new AccountSummary(
                account.getId(), account.getProvider(), displayName(account),
                account.getEmail() == null ? "" : account.getEmail(), account.getStatus(),
                providers.get(account.getProvider()).capabilities()))
            .toList();
    }

    public List<AccountReference> activeAccounts(UUID userId) {
        return accounts.findByUserId(userId).stream()
            .filter(account -> "ACTIVE".equals(account.getStatus()))
            .map(AccountReference::from)
            .toList();
    }

    public Optional<AccountReference> findOwned(UUID userId, UUID accountId) {
        return findOwnedAccount(userId, accountId).map(AccountReference::from);
    }

    public AccountReference requireOwned(UUID userId, UUID accountId) {
        return AccountReference.from(requireOwnedAccount(userId, accountId));
    }

    public StorageQuota quota(UUID userId, UUID accountId) {
        AccountReference account = requireOwned(userId, accountId);
        return providers.get(account.provider()).getQuota(tokens.accessToken(account.id()));
    }

    public void disconnect(UUID userId, UUID accountId) {
        CloudAccount account = requireOwnedAccount(userId, accountId);
        if (account.getRefreshTokenEnc() != null) {
            try {
                providers.get(account.getProvider()).revoke(cipher.decrypt(account.getRefreshTokenEnc()));
            } catch (Exception exception) {
                account.setStatus("NEEDS_MANUAL_REVOKE");
            }
        }
        accounts.delete(account);
    }

    @Transactional
    public void link(User user, ProviderType provider, AccountInfo info, TokenSet tokens) {
        CloudAccount account = accounts.findByProviderAndExternalAccountId(provider, info.externalId())
            .orElseGet(() -> new CloudAccount(user, provider, info.externalId()));
        if (!user.getId().equals(account.getUser().getId())) {
            throw new IllegalArgumentException("Cloud account is already connected to another user");
        }
        account.setDetails(info.displayName(), info.email(), info.scopes());
        account.setTokens(cipher.encrypt(tokens.accessToken()), cipher.encrypt(tokens.refreshToken()), tokens.expiresAt());
        accounts.save(account);
    }

    private Optional<CloudAccount> findOwnedAccount(UUID userId, UUID accountId) {
        return accounts.findById(accountId).filter(account -> account.getUser().getId().equals(userId));
    }

    private CloudAccount requireOwnedAccount(UUID userId, UUID accountId) {
        return findOwnedAccount(userId, accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    private static String displayName(CloudAccount account) {
        if (account.getDisplayName() != null) return account.getDisplayName();
        return switch (account.getProvider()) {
            case GOOGLE -> "Google Drive";
            case DROPBOX -> "Dropbox";
            case ONEDRIVE -> "OneDrive";
        };
    }

    public record AccountReference(UUID id, ProviderType provider, String displayName) {
        private static AccountReference from(CloudAccount account) {
            return new AccountReference(account.getId(), account.getProvider(), account.getDisplayName());
        }
    }

    public record AccountSummary(UUID id, ProviderType provider, String displayName, String email,
                                 String status, Capabilities capabilities) {}
}
