package com.cloudhub.cloud.transfer.application;

import com.cloudhub.cloud.account.application.AccessTokenService;
import com.cloudhub.cloud.account.application.CloudAccountService;
import com.cloudhub.provider.CloudProvider;
import com.cloudhub.provider.DownloadStream;
import com.cloudhub.provider.FileItem;
import com.cloudhub.provider.GoogleApiException;
import com.cloudhub.provider.ProviderRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private final CloudAccountService accounts;
    private final ProviderRegistry providers;
    private final AccessTokenService tokens;
    private final MoveCompletionService moves;

    public TransferService(CloudAccountService accounts, ProviderRegistry providers, AccessTokenService tokens,
                           MoveCompletionService moves) {
        this.accounts = accounts;
        this.providers = providers;
        this.tokens = tokens;
        this.moves = moves;
    }

    public TransferResult transfer(UUID userId, TransferCommand request) throws IOException {
        if (request == null
            || request.sourceAccountId() == null
            || request.destinationAccountId() == null
            || request.sourceFileId() == null
            || request.sourceFileId().isBlank()) {
            throw new IllegalArgumentException("Source and destination account/file are required");
        }

        var sourceAccount = accounts.requireOwned(userId, request.sourceAccountId());
        var destinationAccount = accounts.requireOwned(userId, request.destinationAccountId());
        if (sourceAccount.id().equals(destinationAccount.id())) {
            throw new IllegalArgumentException("Cross-cloud transfer requires two different cloud accounts");
        }
        CloudProvider sourceProvider = providers.get(sourceAccount.provider());
        CloudProvider destinationProvider = providers.get(destinationAccount.provider());

        String sourceToken = tokens.accessToken(sourceAccount.id());
        String destinationToken = tokens.accessToken(destinationAccount.id());
        FileItem metadata = sourceProvider.getMetadata(
            sourceToken, request.sourceFileId(), sourceAccount.id().toString()
        );
        if (metadata.type().equals("FOLDER")) {
            throw new IllegalArgumentException("Folder transfer is not supported; select a file");
        }
        if (metadata.shortcut()) {
            throw new IllegalArgumentException("Shortcuts cannot be transferred");
        }
        if (!destinationProvider.capabilities().upload()) {
            throw new IllegalArgumentException("Destination account does not support upload");
        }

        boolean move = "MOVE".equalsIgnoreCase(request.mode());
        if (request.mode() != null
            && !request.mode().isBlank()
            && !move
            && !"COPY".equalsIgnoreCase(request.mode())) {
            throw new IllegalArgumentException("Transfer mode must be COPY or MOVE");
        }

        Path temporaryFile = Files.createTempFile("cloudhub-transfer-", null);
        try {
            DownloadStream download = sourceProvider.download(sourceToken, metadata.id());
            try (InputStream input = download.input();
                 OutputStream output = Files.newOutputStream(
                     temporaryFile,
                     StandardOpenOption.TRUNCATE_EXISTING
                 )) {
                input.transferTo(output);
            }
            long size = Files.size(temporaryFile);
            try (InputStream uploadInput = Files.newInputStream(temporaryFile)) {
                FileItem copied = destinationProvider.upload(
                    destinationToken,
                    request.destinationFolderId(),
                    download.filename(),
                    download.mimeType(),
                    uploadInput,
                    size,
                    destinationAccount.id().toString()
                );
                if (move) {
                    try {
                        sourceProvider.trash(sourceToken, metadata.id());
                    } catch (GoogleApiException | IllegalStateException failure) {
                        UUID retryId = moves.rememberFailure(userId, sourceAccount.id(), metadata.id());
                        return new TransferResult(copied, false, request.sourceFileId(), retryId);
                    }
                }
                return new TransferResult(copied, move, request.sourceFileId(), null);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    public boolean retryMove(UUID userId, UUID retryId) {
        return moves.retry(retryId, userId);
    }

    public record TransferCommand(
        UUID sourceAccountId,
        String sourceFileId,
        UUID destinationAccountId,
        String destinationFolderId,
        String mode
    ) {}

    public record TransferResult(
        FileItem copiedFile,
        boolean moved,
        String sourceFileId,
        UUID retryId
    ) {}
}
