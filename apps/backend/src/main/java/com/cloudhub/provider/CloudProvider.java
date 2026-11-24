package com.cloudhub.provider;

import java.net.URI;
import java.io.InputStream;

public interface CloudProvider {
    ProviderType type();
    Capabilities capabilities();
    URI buildAuthorizationUrl(String state, String codeChallenge);
    TokenSet exchangeCode(String code, String codeVerifier);
    TokenSet refresh(String refreshToken);
    AccountInfo getAccountInfo(String accessToken);
    Page<FileItem> listRoot(String accessToken, String folderId, String pageToken, String accountId);
    FileItem getMetadata(String accessToken, String fileId, String accountId);
    DownloadStream download(String accessToken, String fileId);
    FileItem upload(String accessToken, String parentId, String name, String mimeType, InputStream data, long size, String accountId);
}
