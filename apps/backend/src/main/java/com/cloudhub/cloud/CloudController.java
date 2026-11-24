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
import com.cloudhub.provider.Page;
import com.cloudhub.security.TokenCipher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import com.cloudhub.provider.DownloadStream;

@RestController
@RequestMapping("/api")
public class CloudController {
    private final CloudAccountRepository accounts;
    private final UserRepository users;
    private final GoogleDriveProvider google;
    private final TokenCipher cipher;

    public CloudController(
        CloudAccountRepository accounts,
        UserRepository users,
        GoogleDriveProvider google,
        TokenCipher cipher
    ) {
        this.accounts = accounts;
        this.users = users;
        this.google = google;
        this.cipher = cipher;
    }

    @GetMapping("/accounts")
    public List<Map<String, Object>> listAccounts(HttpSession session) {
        User user = currentUser(session);
        return accounts.findByUserId(user.getId()).stream()
            .map(account -> Map.<String, Object>of(
                "id", account.getId().toString(),
                "provider", account.getProvider().name(),
                "displayName", account.getDisplayName() == null ? "Google Drive" : account.getDisplayName(),
                "email", account.getEmail() == null ? "" : account.getEmail(),
                "status", account.getStatus()
            ))
            .toList();
    }

    @GetMapping("/accounts/{accountId}/files")
    @Transactional
    public Map<String, Object> listFiles(
        @PathVariable UUID accountId,
        @RequestParam(required = false) String folderId,
        @RequestParam(required = false) String pageToken,
        HttpSession session
    ) {
        User user = currentUser(session);
        CloudAccount account = ownedAccount(accountId, user);
        String accessToken = ensureAccessToken(account);
        Page<FileItem> page = google.listRoot(accessToken, folderId, pageToken, accountId.toString());
        return Map.of(
            "items", page.items(),
            "nextPageToken", page.nextPageToken() == null ? "" : page.nextPageToken()
        );
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
        return google.getMetadata(ensureAccessToken(account), fileId, accountId.toString());
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
        DownloadStream download = google.download(ensureAccessToken(account), fileId);
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
        return google.upload(
            ensureAccessToken(account),
            parentId,
            file.getOriginalFilename(),
            mimeType,
            file.getInputStream(),
            file.getSize(),
            accountId.toString()
        );
    }

    @DeleteMapping("/accounts/{accountId}")
    public Map<String, Boolean> disconnect(
        @PathVariable UUID accountId,
        HttpSession session
    ) {
        User user = currentUser(session);
        accounts.delete(ownedAccount(accountId, user));
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
            com.cloudhub.provider.TokenSet refreshed = google.refresh(refreshToken);
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
