package com.cloudhub.cloud;

import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

import com.cloudhub.auth.AuthController;
import com.cloudhub.auth.User;
import com.cloudhub.auth.UserRepository;
import com.cloudhub.provider.FileItem;
import com.cloudhub.provider.GoogleDriveProvider;
import com.cloudhub.provider.DropboxProvider;
import com.cloudhub.provider.OneDriveProvider;
import com.cloudhub.provider.CloudProvider;
import com.cloudhub.provider.ProviderType;
import com.cloudhub.provider.Page;
import com.cloudhub.security.TokenCipher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import com.cloudhub.provider.DownloadStream;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class CloudController {
    private final CloudAccountRepository accounts;
    private final UserRepository users;
    private final GoogleDriveProvider google;
    private final DropboxProvider dropbox;
    private final OneDriveProvider onedrive;
    private final TokenCipher cipher;

    public CloudController(
        CloudAccountRepository accounts,
        UserRepository users,
        GoogleDriveProvider google,
        TokenCipher cipher
    ) {
        this(accounts, users, google, null, null, cipher);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CloudController(
        CloudAccountRepository accounts,
        UserRepository users,
        GoogleDriveProvider google,
        DropboxProvider dropbox,
        OneDriveProvider onedrive,
        TokenCipher cipher
    ) {
        this.accounts = accounts;
        this.users = users;
        this.google = google;
        this.dropbox = dropbox;
        this.onedrive = onedrive;
        this.cipher = cipher;
    }

    @GetMapping("/accounts")
    public List<Map<String, Object>> listAccounts(HttpSession session) {
        User user = currentUser(session);
        return accounts.findByUserId(user.getId()).stream()
            .map(account -> Map.<String, Object>of(
                "id", account.getId().toString(),
                "provider", account.getProvider().name(),
                "displayName", account.getDisplayName() == null
                    ? switch (account.getProvider()) {
                        case ONEDRIVE -> "OneDrive";
                        case DROPBOX -> "Dropbox";
                        case GOOGLE -> "Google Drive";
                    }
                    : account.getDisplayName(),
                "email", account.getEmail() == null ? "" : account.getEmail(),
                "status", account.getStatus(),
                "capabilities", provider(account).capabilities()
            ))
            .toList();
    }

    @GetMapping("/accounts/{accountId}/files")
    @Transactional
    public Map<String, Object> listFiles(
        @PathVariable UUID accountId,
        @RequestParam(required = false) String folderId,
        @RequestParam(required = false) String pageToken,
        @RequestParam(required = false, defaultValue = "all") String type,
        @RequestParam(required = false, defaultValue = "name") String sort,
        HttpSession session
    ) {
        User user = currentUser(session);
        CloudAccount account = ownedAccount(accountId, user);
        String accessToken = ensureAccessToken(account);
        Page<FileItem> page = provider(account).listRoot(
            accessToken, folderId, pageToken, accountId.toString(), type, sort
        );
        return Map.of(
            "items", page.items(),
            "nextPageToken", page.nextPageToken() == null ? "" : page.nextPageToken()
        );
    }

    @GetMapping("/search")
    @Transactional
    public Map<String, Object> searchFiles(
        @RequestParam String q,
        @RequestParam(required = false) String accountId,
        @RequestParam(required = false) String providers,
        @RequestParam(required = false) String folderId,
        @RequestParam(required = false) String pageToken,
        @RequestParam(required = false, defaultValue = "all") String type,
        @RequestParam(required = false, defaultValue = "name") String sort,
        HttpSession session
    ) {
        User user = currentUser(session);
        if (accountId != null && !accountId.isBlank()) {
            UUID id = UUID.fromString(accountId);
            CloudAccount account = ownedAccount(id, user);
            Page<FileItem> page = provider(account).search(
                ensureAccessToken(account), q, folderId, pageToken, id.toString(), type, sort
            );
            return Map.of(
                "items", page.items(),
                "nextPageToken", page.nextPageToken() == null ? "" : page.nextPageToken()
            );
        }

        List<CloudAccount> selected = accounts.findByUserId(user.getId()).stream()
            .filter(account -> providers == null || providers.isBlank()
                || containsProvider(providers, account.getProvider()))
            .filter(account -> account.getStatus().equals("ACTIVE"))
            .toList();

        List<CompletableFuture<Map<String, Object>>> searches = new ArrayList<>();
        for (CloudAccount account : selected) {
            searches.add(CompletableFuture.supplyAsync(() -> {
                try {
                    Page<FileItem> page = provider(account).search(
                        ensureAccessToken(account), q, folderId, pageToken,
                        account.getId().toString(), type, sort
                    );
                    return Map.<String, Object>of(
                        "accountId", account.getId().toString(),
                        "accountName", account.getDisplayName() == null ? account.getProvider().name() : account.getDisplayName(),
                        "provider", account.getProvider().name(),
                        "status", "ok",
                        "items", page.items(),
                        "nextPageToken", page.nextPageToken() == null ? "" : page.nextPageToken()
                    );
                } catch (Exception exception) {
                    String message = exception.getMessage() == null ? "Provider search failed" : exception.getMessage();
                    return Map.<String, Object>of(
                        "accountId", account.getId().toString(),
                        "accountName", account.getDisplayName() == null ? account.getProvider().name() : account.getDisplayName(),
                        "provider", account.getProvider().name(),
                        "status", "error",
                        "items", List.of(),
                        "error", message
                    );
                }
            }).completeOnTimeout(Map.of(
                "accountId", account.getId().toString(),
                "accountName", account.getDisplayName() == null ? account.getProvider().name() : account.getDisplayName(),
                "provider", account.getProvider().name(),
                "status", "error",
                "items", List.of(),
                "error", "Provider search timed out"
            ), 10, TimeUnit.SECONDS));
        }

        List<Map<String, Object>> groups = searches.stream()
            .map(CompletableFuture::join)
            .toList();
        return Map.of("q", q, "groups", groups);
    }

    @GetMapping("/accounts/{accountId}/quota")
    @Transactional
    public com.cloudhub.provider.StorageQuota getQuota(
        @PathVariable UUID accountId,
        HttpSession session
    ) {
        User user = currentUser(session);
        CloudAccount account = ownedAccount(accountId, user);
        return provider(account).getQuota(ensureAccessToken(account));
    }

    @GetMapping("/accounts/{accountId}/files/{fileId}")
    @Transactional
    public FileItem getFile(
        @PathVariable UUID accountId,
        @PathVariable String fileId,
        HttpSession session
    ) {
        User user = currentUser(session);
        CloudAccount account = ownedAccount(accountId, user);
        return provider(account).getMetadata(ensureAccessToken(account), fileId, accountId.toString());
    }

    @GetMapping("/accounts/{accountId}/files/{fileId}/content")
    @Transactional
    public ResponseEntity<StreamingResponseBody> downloadFile(
        @PathVariable UUID accountId,
        @PathVariable String fileId,
        @RequestParam(defaultValue = "attachment") String disposition,
        HttpSession session
    ) {
        User user = currentUser(session);
        CloudAccount account = ownedAccount(accountId, user);
        DownloadStream download = provider(account).download(ensureAccessToken(account), fileId);
        String safeFilename = URLEncoder.encode(download.filename(), StandardCharsets.UTF_8)
            .replace("+", "%20");
        String contentDisposition = disposition.equals("inline") ? "inline" : "attachment";
        contentDisposition += "; filename*=UTF-8''" + safeFilename;

        StreamingResponseBody body = output -> {
            try (java.io.InputStream input = download.input()) {
                input.transferTo(output);
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.mimeType()));
        headers.set("Content-Disposition", contentDisposition);
        headers.set("X-Content-Type-Options", "nosniff");
        if (download.size() >= 0) headers.setContentLength(download.size());
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @PostMapping("/accounts/{accountId}/files")
    @Transactional
    public FileItem uploadFile(
        @PathVariable UUID accountId,
        @RequestParam(required = false) String parentId,
        @RequestPart("file") MultipartFile file,
        HttpSession session
    ) throws IOException {
        User user = currentUser(session);
        CloudAccount account = ownedAccount(accountId, user);
        String mimeType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        return provider(account).upload(
            ensureAccessToken(account),
            parentId,
            file.getOriginalFilename(),
            mimeType,
            file.getInputStream(),
            file.getSize(),
            accountId.toString()
        );
    }

    @GetMapping("/accounts/{accountId}/files/{fileId}/thumbnail")
    @Transactional
    public ResponseEntity<StreamingResponseBody> thumbnail(
        @PathVariable UUID accountId,
        @PathVariable String fileId,
        HttpSession session
    ) {
        User user = currentUser(session);
        CloudAccount account = ownedAccount(accountId, user);
        DownloadStream download = provider(account).thumbnail(ensureAccessToken(account), fileId);
        StreamingResponseBody body = output -> {
            try (java.io.InputStream input = download.input()) {
                input.transferTo(output);
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.mimeType()));
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("private, max-age=300");
        if (download.size() >= 0) headers.setContentLength(download.size());
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @PostMapping("/accounts/{accountId}/folders")
    @Transactional
    public FileItem createFolder(
        @PathVariable UUID accountId,
        @RequestParam(required = false) String parentId,
        @RequestParam String name,
        HttpSession session
    ) {
        User user = currentUser(session);
        CloudAccount account = ownedAccount(accountId, user);
        return provider(account).createFolder(
            ensureAccessToken(account), parentId, name, accountId.toString()
        );
    }

    @PatchMapping("/accounts/{accountId}/files/{fileId}")
    @Transactional
    public FileItem renameFile(
        @PathVariable UUID accountId,
        @PathVariable String fileId,
        @RequestParam String name,
        HttpSession session
    ) {
        User user = currentUser(session);
        CloudAccount account = ownedAccount(accountId, user);
        return provider(account).rename(ensureAccessToken(account), fileId, name, accountId.toString());
    }

    @DeleteMapping("/accounts/{accountId}/files/{fileId}")
    @Transactional
    public Map<String, Boolean> trashFile(
        @PathVariable UUID accountId,
        @PathVariable String fileId,
        HttpSession session
    ) {
        User user = currentUser(session);
        CloudAccount account = ownedAccount(accountId, user);
        provider(account).trash(ensureAccessToken(account), fileId);
        return Map.of("trashed", true);
    }

    @DeleteMapping("/accounts/{accountId}")
    @Transactional
    public Map<String, Boolean> disconnect(
        @PathVariable UUID accountId,
        HttpSession session
    ) {
        User user = currentUser(session);
        CloudAccount account = ownedAccount(accountId, user);
        if (account.getRefreshTokenEnc() != null) {
            try {
                provider(account).revoke(cipher.decrypt(account.getRefreshTokenEnc()));
            } catch (Exception exception) {
                account.setStatus("NEEDS_MANUAL_REVOKE");
            }
        }
        accounts.delete(account);
        return Map.of("disconnected", true);
    }

    private CloudAccount ownedAccount(UUID accountId, User user) {
        CloudAccount account = accounts.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Account not found");
        }
        return account;
    }

    private CloudProvider provider(CloudAccount account) {
        if (account.getProvider() == ProviderType.DROPBOX && dropbox != null) return dropbox;
        if (account.getProvider() == ProviderType.GOOGLE) return google;
        if (account.getProvider() == ProviderType.ONEDRIVE && onedrive != null) return onedrive;
        throw new IllegalStateException("Unsupported cloud provider");
    }

    private boolean containsProvider(String providers, ProviderType provider) {
        for (String value : providers.split(",")) {
            if (value.trim().equalsIgnoreCase(provider.name())) return true;
        }
        return false;
    }

    private String ensureAccessToken(CloudAccount account) {
        if (account.getExpiresAt() != null
            && account.getExpiresAt().isAfter(java.time.Instant.now().plusSeconds(60))) {
            return cipher.decrypt(account.getAccessTokenEnc());
        }
        if (account.getRefreshTokenEnc() == null) {
            throw new IllegalStateException("Account needs reconnect");
        }
        String refreshToken = cipher.decrypt(account.getRefreshTokenEnc());
        try {
            com.cloudhub.provider.TokenSet refreshed = provider(account).refresh(refreshToken);
            account.setTokens(
                cipher.encrypt(refreshed.accessToken()),
                refreshed.refreshToken() == null ? null : cipher.encrypt(refreshed.refreshToken()),
                refreshed.expiresAt()
            );
            accounts.save(account);
            return refreshed.accessToken();
        } catch (com.cloudhub.provider.GoogleApiException exception) {
            if (exception.statusCode() == 401) {
                account.setStatus("NEEDS_RECONNECT");
                accounts.save(account);
            }
            throw exception;
        }
    }

    private User currentUser(HttpSession session) {
        Object userId = session.getAttribute(AuthController.SESSION_USER);
        if (userId == null) throw new IllegalArgumentException("Login required");
        return users.findById(UUID.fromString(userId.toString()))
            .orElseThrow(() -> new IllegalArgumentException("Login required"));
    }
}
