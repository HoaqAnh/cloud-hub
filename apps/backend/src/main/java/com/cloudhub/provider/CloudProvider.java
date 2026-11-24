package com.cloudhub.provider;

import java.net.URI;
import java.io.InputStream;

public interface CloudProvider {
    ProviderType type();
    Capabilities capabilities();
    URI buildAuthorizationUrl(String state, String codeChallenge);
    TokenSet exchangeCode(String code, String codeVerifier);
    TokenSet refresh(String refreshToken);
    void revoke(String refreshToken);
    AccountInfo getAccountInfo(String accessToken);
    StorageQuota getQuota(String accessToken);
    Page<FileItem> listRoot(String accessToken, String folderId, String pageToken, String accountId, String type, String sort);
    Page<FileItem> search(String accessToken, String query, String folderId, String pageToken, String accountId, String type, String sort);
    FileItem getMetadata(String accessToken, String fileId, String accountId);
    DownloadStream download(String accessToken, String fileId);
    DownloadStream thumbnail(String accessToken, String fileId);
    FileItem upload(String accessToken, String parentId, String name, String mimeType, InputStream data, long size, String accountId);
    FileItem createFolder(String accessToken, String parentId, String name, String accountId);
    FileItem rename(String accessToken, String fileId, String newName, String accountId);
    void trash(String accessToken, String fileId);
}
