package com.cloudhub.provider;

import java.net.URI;

public interface CloudProvider {
    ProviderType type();
    Capabilities capabilities();
    URI buildAuthorizationUrl(String state, String codeChallenge);
    TokenSet exchangeCode(String code, String codeVerifier);
    TokenSet refresh(String refreshToken);
    AccountInfo getAccountInfo(String accessToken);
    Page<FileItem> listRoot(String accessToken, String folderId, String pageToken, String accountId);
}
