package com.cloudhub.cloud.files.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cloudhub.auth.persistence.User;
import com.cloudhub.cloud.account.application.AccessTokenService;
import com.cloudhub.cloud.account.application.CloudAccountService;
import com.cloudhub.cloud.account.persistence.CloudAccount;
import com.cloudhub.cloud.account.persistence.CloudAccountRepository;
import com.cloudhub.provider.CloudProvider;
import com.cloudhub.provider.FileItem;
import com.cloudhub.provider.Page;
import com.cloudhub.provider.ProviderRegistry;
import com.cloudhub.provider.ProviderType;
import com.cloudhub.security.TokenCipher;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloudFileServiceTest {
    private final CloudAccountRepository repository = mock(CloudAccountRepository.class);
    private final AccessTokenService tokens = mock(AccessTokenService.class);
    private final CloudProvider provider = mock(CloudProvider.class);
    private final User owner = new User(UUID.randomUUID(), "owner", "");
    private final CloudAccount account = new CloudAccount(owner, ProviderType.DROPBOX, "external-id");
    private CloudFileService files;

    @BeforeEach
    void configureAccount() {
        when(provider.type()).thenReturn(ProviderType.DROPBOX);
        when(repository.findById(account.getId())).thenReturn(Optional.of(account));
        var providers = new ProviderRegistry(List.of(provider));
        var accounts = new CloudAccountService(repository, providers, tokens, mock(TokenCipher.class));
        files = new CloudFileService(accounts, providers, tokens);
    }

    @Test
    void listsFilesThroughTheOwnedAccountProvider() {
        when(tokens.accessToken(account.getId())).thenReturn("access");
        Page<FileItem> page = new Page<>(List.of(), "next");
        when(provider.listRoot("access", "folder", "cursor", account.getId().toString(), "file", "-name"))
            .thenReturn(page);

        assertThat(files.list(owner.getId(), account.getId(), "folder", "cursor", "file", "-name")).isEqualTo(page);
    }

    @Test
    void refusesOtherUsersBeforeResolvingAnAccessToken() {
        assertThatThrownBy(() -> files.download(UUID.randomUUID(), account.getId(), "file"))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Account not found");
        verifyNoInteractions(tokens);
    }
}
