package com.cloudhub.cloud;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.cloudhub.auth.AuthController;
import com.cloudhub.auth.User;
import com.cloudhub.auth.UserRepository;
import com.cloudhub.provider.*;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class TransferTest {
    private final CloudAccountRepository accounts = mock(CloudAccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GoogleDriveProvider source = mock(GoogleDriveProvider.class);
    private final DropboxProvider dest = mock(DropboxProvider.class);
    private final AccessTokenService tokens = mock(AccessTokenService.class);
    private final MoveCompletionService moves = mock(MoveCompletionService.class);
    private final User user = new User(UUID.randomUUID(), "transfer-test", "");
    private final CloudAccount sourceAccount = new CloudAccount(user, ProviderType.GOOGLE, "source");
    private final CloudAccount destAccount = new CloudAccount(user, ProviderType.DROPBOX, "dest");
    private final MockHttpSession session = new MockHttpSession();
    private final FileItem copied = file("destination-file", ProviderType.DROPBOX);
    private CloudController controller;

    @BeforeEach
    void setup() {
        when(source.type()).thenReturn(ProviderType.GOOGLE);
        when(dest.type()).thenReturn(ProviderType.DROPBOX);
        controller = new CloudController(accounts, users, new ProviderRegistry(List.of(source, dest)), tokens, null, moves, null);
        session.setAttribute(AuthController.SESSION_USER, user.getId().toString());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(accounts.findById(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accounts.findById(destAccount.getId())).thenReturn(Optional.of(destAccount));
        when(tokens.accessToken(sourceAccount.getId())).thenReturn("source-token");
        when(tokens.accessToken(destAccount.getId())).thenReturn("dest-token");
        when(source.getMetadata("source-token", "source-file", sourceAccount.getId().toString()))
            .thenReturn(file("source-file", ProviderType.GOOGLE));
        when(source.download("source-token", "source-file")).thenAnswer(call ->
            new DownloadStream(new ByteArrayInputStream(new byte[]{1}), 1, "text/plain", "test.txt"));
        when(dest.capabilities()).thenReturn(new Capabilities(true, true, true, true, true));
        when(dest.upload(eq("dest-token"), eq("folder"), eq("test.txt"), eq("text/plain"), any(), eq(1L),
            eq(destAccount.getId().toString()))).thenReturn(copied);
    }

    @Test
    void keepsSourceIfUploadFails() {
        when(dest.upload(anyString(), anyString(), anyString(), anyString(), any(), anyLong(), anyString()))
            .thenThrow(new GoogleApiException(503, "Unavailable"));
        assertThatThrownBy(() -> transfer("MOVE")).isInstanceOf(GoogleApiException.class);
        verify(source, never()).trash(anyString(), anyString());
        verifyNoInteractions(moves);
    }

    @Test
    void exposesCopiedFileAndRetryWhenSourceTrashFails() throws Exception {
        UUID retry = UUID.randomUUID();
        when(moves.rememberFailure(user.getId(), sourceAccount.getId(), "source-file")).thenReturn(retry);
        doThrow(new GoogleApiException(503, "Unavailable")).when(source).trash("source-token", "source-file");
        var result = transfer("MOVE");
        assertThat(result.copiedFile()).isEqualTo(copied);
        assertThat(result.moved()).isFalse();
        assertThat(result.retryId()).isEqualTo(retry);
    }

    @Test
    void copyAndMoveKeepTheirSuccessBehavior() throws Exception {
        assertThat(transfer("COPY").moved()).isFalse();
        verify(source, never()).trash(anyString(), anyString());
        var result = transfer("MOVE");
        assertThat(result.moved()).isTrue();
        assertThat(result.retryId()).isNull();
        verify(source).trash("source-token", "source-file");
    }

    private CloudController.TransferResult transfer(String mode) throws Exception {
        return controller.transferFile(new CloudController.TransferRequest(sourceAccount.getId(), "source-file",
            destAccount.getId(), "folder", mode), session);
    }

    private static FileItem file(String id, ProviderType provider) {
        return new FileItem(id, null, provider, "test.txt", "FILE", "text/plain", 1L, null,
            null, false, null, false, null, false);
    }
}
