package com.cloudhub.cloud.files.application;

import com.cloudhub.cloud.account.application.AccessTokenService;
import com.cloudhub.cloud.account.application.CloudAccountService;
import com.cloudhub.provider.CloudProvider;
import com.cloudhub.provider.DownloadStream;
import com.cloudhub.provider.FileItem;
import com.cloudhub.provider.Page;
import com.cloudhub.provider.ProviderRegistry;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CloudFileService {
    private final CloudAccountService accounts;
    private final ProviderRegistry providers;
    private final AccessTokenService tokens;

    public CloudFileService(CloudAccountService accounts, ProviderRegistry providers, AccessTokenService tokens) {
        this.accounts = accounts;
        this.providers = providers;
        this.tokens = tokens;
    }

    public Page<FileItem> list(UUID userId, UUID accountId, String folderId, String pageToken, String type, String sort) {
        var access = access(userId, accountId);
        return access.provider().listRoot(access.token(), folderId, pageToken, accountId.toString(), type, sort);
    }

    public FileItem metadata(UUID userId, UUID accountId, String fileId) {
        var access = access(userId, accountId);
        return access.provider().getMetadata(access.token(), fileId, accountId.toString());
    }

    public DownloadStream download(UUID userId, UUID accountId, String fileId) {
        var access = access(userId, accountId);
        return access.provider().download(access.token(), fileId);
    }

    public FileItem upload(UUID userId, UUID accountId, String parentId, String name, String mimeType,
                           InputStream input, long size) {
        var access = access(userId, accountId);
        return access.provider().upload(access.token(), parentId, name, mimeType, input, size, accountId.toString());
    }

    public DownloadStream thumbnail(UUID userId, UUID accountId, String fileId) {
        var access = access(userId, accountId);
        return access.provider().thumbnail(access.token(), fileId);
    }

    public FileItem createFolder(UUID userId, UUID accountId, String parentId, String name) {
        var access = access(userId, accountId);
        return access.provider().createFolder(access.token(), parentId, name, accountId.toString());
    }

    public FileItem rename(UUID userId, UUID accountId, String fileId, String name) {
        var access = access(userId, accountId);
        return access.provider().rename(access.token(), fileId, name, accountId.toString());
    }

    public void trash(UUID userId, UUID accountId, String fileId) {
        var access = access(userId, accountId);
        access.provider().trash(access.token(), fileId);
    }

    private FileAccess access(UUID userId, UUID accountId) {
        var account = accounts.requireOwned(userId, accountId);
        return new FileAccess(providers.get(account.provider()), tokens.accessToken(account.id()));
    }

    private record FileAccess(CloudProvider provider, String token) {}
}
