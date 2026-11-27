package com.cloudhub.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class OneDriveProvider implements CloudProvider {
    private final String clientId;
    private final String clientSecret;
    private final URI redirectUri;
    private final String tenant;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public OneDriveProvider(
        @Value("${onedrive.client-id:}") String clientId,
        @Value("${onedrive.client-secret:}") String clientSecret,
        @Value("${onedrive.redirect-uri}") String redirectUri,
        @Value("${onedrive.tenant:common}") String tenant
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = URI.create(redirectUri);
        this.tenant = tenant.isBlank() ? "common" : tenant.trim();
    }

    @Override public ProviderType type() { return ProviderType.ONEDRIVE; }
    @Override public Capabilities capabilities() {
        return new Capabilities(true, true, true, true, true);
    }

    @Override
    public URI buildAuthorizationUrl(String state, String codeChallenge) {
        requireConfigured();
        String scopes = "openid profile email offline_access User.Read Files.Read Files.ReadWrite";
        String query = "client_id=" + encode(clientId)
            + "&redirect_uri=" + encode(redirectUri.toString())
            + "&response_type=code"
            + "&scope=" + encode(scopes)
            + "&state=" + encode(state)
            + "&code_challenge=" + encode(codeChallenge)
            + "&code_challenge_method=S256"
            + "&prompt=consent";
        return URI.create("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/authorize?" + query);
    }

    public String createCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String createCodeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create PKCE challenge", exception);
        }
    }

    @Override
    public TokenSet exchangeCode(String code, String codeVerifier) {
        requireConfigured();
        String body = "client_id=" + encode(clientId)
            + "&client_secret=" + encode(clientSecret)
            + "&code=" + encode(code)
            + "&redirect_uri=" + encode(redirectUri.toString())
            + "&grant_type=authorization_code"
            + "&code_verifier=" + encode(codeVerifier);
        JsonNode json = postForm("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token", body);
        return tokenSet(json);
    }

    @Override
    public TokenSet refresh(String refreshToken) {
        requireConfigured();
        String body = "client_id=" + encode(clientId)
            + "&client_secret=" + encode(clientSecret)
            + "&refresh_token=" + encode(refreshToken)
            + "&grant_type=refresh_token";
        JsonNode json = postForm("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token", body);
        return tokenSet(json);
    }

    @Override
    public void revoke(String refreshToken) {
        // Microsoft Graph has no general refresh-token revoke endpoint.
    }

    private TokenSet tokenSet(JsonNode json) {
        if (!json.has("access_token")) throw new IllegalStateException("Microsoft returned no access token");
        long expiresIn = json.path("expires_in").asLong(3600);
        String refresh = json.has("refresh_token") ? json.get("refresh_token").asText() : null;
        return new TokenSet(json.get("access_token").asText(), refresh, Instant.now().plusSeconds(expiresIn));
    }

    @Override
    public AccountInfo getAccountInfo(String accessToken) {
        JsonNode json = getJson("https://graph.microsoft.com/v1.0/me?$select=id,displayName,userPrincipalName", accessToken);
        return new AccountInfo(
            json.path("id").asText(),
            json.path("displayName").asText("OneDrive account"),
            json.path("userPrincipalName").asText(),
            "openid profile email offline_access User.Read Files.Read Files.ReadWrite"
        );
    }

    @Override
    public StorageQuota getQuota(String accessToken) {
        JsonNode quota = getJson("https://graph.microsoft.com/v1.0/me/drive?$select=quota", accessToken).path("quota");
        return new StorageQuota(
            quota.path("total").asLong(0),
            quota.path("used").asLong(0),
            quota.path("used").asLong(0),
            quota.path("deleted").asLong(0)
        );
    }

    @Override
    public Page<FileItem> listRoot(
        String accessToken,
        String folderId,
        String pageToken,
        String accountId,
        String type,
        String sort
    ) {
        String url;
        if (pageToken != null && !pageToken.isBlank()) {
            url = pageToken;
        } else {
            String base = folderId == null || folderId.isBlank()
                ? "https://graph.microsoft.com/v1.0/me/drive/root/children"
                : "https://graph.microsoft.com/v1.0/me/drive/items/" + encodePath(folderId) + "/children";
            url = base + "?$top=200&$select=id,name,size,file,folder,parentReference,lastModifiedDateTime,remoteItem";
        }
        JsonNode json = getJson(url, accessToken);
        List<FileItem> items = mapItems(json.path("value"), accountId);
        items = filterAndSort(items, type, sort);
        return new Page<>(items, json.path("@odata.nextLink").asText(null));
    }

    @Override
    public Page<FileItem> search(
        String accessToken,
        String query,
        String folderId,
        String pageToken,
        String accountId,
        String type,
        String sort
    ) {
        String url;
        if (pageToken != null && !pageToken.isBlank()) {
            url = pageToken;
        } else {
            String escaped = (query == null ? "" : query).replace("'", "''");
            url = "https://graph.microsoft.com/v1.0/me/drive/root/search(q='" + encode(escaped)
                + "')?$top=200&$select=id,name,size,file,folder,parentReference,lastModifiedDateTime,remoteItem";
        }
        JsonNode json = getJson(url, accessToken);
        List<FileItem> items = mapItems(json.path("value"), accountId);
        items = filterAndSort(items, type, sort);
        return new Page<>(items, json.path("@odata.nextLink").asText(null));
    }

    @Override
    public FileItem getMetadata(String accessToken, String fileId, String accountId) {
        JsonNode item = getJson(
            "https://graph.microsoft.com/v1.0/me/drive/items/" + encodePath(fileId)
                + "?$select=id,name,size,file,folder,parentReference,lastModifiedDateTime,remoteItem",
            accessToken
        );
        return mapItem(item, accountId);
    }

    @Override
    public DownloadStream download(String accessToken, String fileId) {
        FileItem metadata = getMetadata(accessToken, fileId, "");
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("https://graph.microsoft.com/v1.0/me/drive/items/" + encodePath(fileId) + "/content"))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) throw new GoogleApiException(response.statusCode(), "OneDrive download failed");
            long size = response.headers().firstValueAsLong("Content-Length").orElse(metadata.size() == null ? -1 : metadata.size());
            return new DownloadStream(response.body(), size, metadata.mimeType(), metadata.name());
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot download file from OneDrive", exception);
        }
    }

    @Override
    public DownloadStream thumbnail(String accessToken, String fileId) {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("https://graph.microsoft.com/v1.0/me/drive/items/" + encodePath(fileId)
                    + "/thumbnails/0/small/content"))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) throw new GoogleApiException(response.statusCode(), "OneDrive thumbnail failed");
            String mime = response.headers().firstValue("Content-Type").orElse("image/jpeg");
            long size = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            return new DownloadStream(response.body(), size, mime, "thumbnail");
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot download OneDrive thumbnail", exception);
        }
    }

    @Override
    public FileItem upload(
        String accessToken,
        String parentId,
        String name,
        String mimeType,
        InputStream data,
        long size,
        String accountId
    ) {
        String safeName = name == null || name.isBlank() ? "Untitled" : name;
        try {
            String itemPath = parentId == null || parentId.isBlank()
                ? "/me/drive/root:/" + encodePath(safeName) + ":/content"
                : "/me/drive/items/" + encodePath(parentId) + ":/" + encodePath(safeName) + ":/content";
            JsonNode json = null;
            if (size <= 4 * 1024 * 1024) {
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://graph.microsoft.com/v1.0" + itemPath))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType)
                    .PUT(HttpRequest.BodyPublishers.fromPublisher(
                        HttpRequest.BodyPublishers.ofInputStream(() -> data), size))
                    .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) throw new GoogleApiException(response.statusCode(), "OneDrive upload failed");
                json = mapper.readTree(response.body());
            } else {
                String sessionUrl = parentId == null || parentId.isBlank()
                    ? "https://graph.microsoft.com/v1.0/me/drive/root:/"
                        + encodePath(safeName) + ":/createUploadSession"
                    : "https://graph.microsoft.com/v1.0/me/drive/items/" + encodePath(parentId)
                        + ":/" + encodePath(safeName) + ":/createUploadSession";
                String sessionBody = "{\"item\":{\"@microsoft.graph.conflictBehavior\":\"rename\"}}";
                HttpRequest sessionRequest = HttpRequest.newBuilder(URI.create(sessionUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(sessionBody))
                    .build();
                HttpResponse<String> sessionResponse = http.send(sessionRequest, HttpResponse.BodyHandlers.ofString());
                if (sessionResponse.statusCode() >= 400) throw new GoogleApiException(sessionResponse.statusCode(), "OneDrive upload session could not start");
                String uploadUrl = mapper.readTree(sessionResponse.body()).path("uploadUrl").asText();
                if (uploadUrl.isBlank()) throw new IllegalStateException("OneDrive did not return an upload URL");

                byte[] buffer = new byte[5 * 1024 * 1024];
                long offset = 0;
                while (offset < size) {
                    int requested = (int) Math.min((long) buffer.length, size - offset);
                    int read = data.readNBytes(buffer, 0, requested);
                    if (read <= 0) throw new IllegalStateException("OneDrive upload stream ended before the declared size");
                    long end = offset + read - 1;
                    HttpRequest chunkRequest = HttpRequest.newBuilder(URI.create(uploadUrl))
                        .header("Content-Length", Integer.toString(read))
                        .header("Content-Range", "bytes " + offset + "-" + end + "/" + size)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(java.util.Arrays.copyOf(buffer, read)))
                        .build();
                    HttpResponse<String> chunkResponse = http.send(chunkRequest, HttpResponse.BodyHandlers.ofString());
                    if (chunkResponse.statusCode() >= 400 && chunkResponse.statusCode() != 202)
                        throw new GoogleApiException(chunkResponse.statusCode(), "OneDrive upload session failed");
                    offset += read;
                    if (offset == size) json = mapper.readTree(chunkResponse.body());
                }
            }
            if (json == null) {
                throw new IllegalStateException("OneDrive upload completed without returning file metadata");
            }
            return mapItem(json, accountId);
        } catch (GoogleApiException | IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot upload to OneDrive", exception);
        }
    }

    @Override
    public FileItem createFolder(String accessToken, String parentId, String name, String accountId) {
        String url = parentId == null || parentId.isBlank()
            ? "https://graph.microsoft.com/v1.0/me/drive/root/children"
            : "https://graph.microsoft.com/v1.0/me/drive/items/" + encodePath(parentId) + "/children";
        String body;
        try {
            body = mapper.writeValueAsString(java.util.Map.of(
                "name", name == null || name.isBlank() ? "Untitled" : name,
                "folder", java.util.Map.of(),
                "@microsoft.graph.conflictBehavior", "rename"
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot build OneDrive folder request", exception);
        }
        JsonNode json = sendJson(HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build());
        return mapItem(json, accountId);
    }

    @Override
    public FileItem rename(String accessToken, String fileId, String newName, String accountId) {
        String body;
        try {
            body = mapper.writeValueAsString(java.util.Map.of("name",
                newName == null || newName.isBlank() ? "Untitled" : newName));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot build OneDrive rename request", exception);
        }
        JsonNode json = sendJson(HttpRequest.newBuilder(
                URI.create("https://graph.microsoft.com/v1.0/me/drive/items/" + encodePath(fileId)))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .build());
        return mapItem(json, accountId);
    }

    @Override
    public void trash(String accessToken, String fileId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("https://graph.microsoft.com/v1.0/me/drive/items/" + encodePath(fileId)))
                .header("Authorization", "Bearer " + accessToken)
                .DELETE()
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400)
                throw new GoogleApiException(response.statusCode(), "Could not move OneDrive item to trash");
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot trash OneDrive item", exception);
        }
    }

    private List<FileItem> mapItems(JsonNode nodes, String accountId) {
        List<FileItem> items = new ArrayList<>();
        for (JsonNode node : nodes) items.add(mapItem(node, accountId));
        return items;
    }

    private FileItem mapItem(JsonNode item, String accountId) {
        boolean folder = item.has("folder");
        JsonNode remote = item.path("remoteItem");
        boolean shortcut = !remote.isMissingNode() && !remote.isNull();
        String mime = item.path("file").path("mimeType").asText(
            folder ? "application/vnd.microsoft.folder" : "application/octet-stream");
        if (shortcut && !item.path("name").isMissingNode() && !folder) {
            mime = item.path("remoteItem").path("file").path("mimeType").asText(mime);
            folder = item.path("remoteItem").has("folder");
        }
        String parentId = item.path("parentReference").path("id").asText(null);
        if ((parentId == null || parentId.isBlank()) && shortcut)
            parentId = remote.path("parentReference").path("id").asText(null);
        Instant modified = null;
        if (item.has("lastModifiedDateTime") && !item.path("lastModifiedDateTime").isNull()) {
            try { modified = Instant.parse(item.path("lastModifiedDateTime").asText()); } catch (Exception ignored) {}
        }
        String thumbnailUrl = !folder && !mime.isBlank() ? "/thumbnail" : null;
        boolean previewable = mime.startsWith("image/")
            || "application/pdf".equals(mime);
        Long size = item.has("size") && !item.path("size").isNull()
            ? item.path("size").asLong() : null;
        String targetId = shortcut ? remote.path("id").asText(null) : null;
        return new FileItem(
            item.path("id").asText(),
            accountId,
            ProviderType.ONEDRIVE,
            item.path("name").asText("Untitled"),
            folder ? "FOLDER" : "FILE",
            mime,
            size,
            modified,
            parentId,
            shortcut,
            targetId,
            false,
            thumbnailUrl,
            previewable
        );
    }

    private List<FileItem> filterAndSort(List<FileItem> items, String type, String sort) {
        List<FileItem> result = new ArrayList<>();
        for (FileItem item : items) {
            if (matchesType(item, type)) result.add(item);
        }
        Comparator<FileItem> comparator = switch (sort == null ? "name" : sort) {
            case "name-desc" -> Comparator.comparing(FileItem::name, String.CASE_INSENSITIVE_ORDER).reversed();
            case "size" -> Comparator.comparing(item -> item.size() == null ? 0L : item.size());
            case "size-desc" -> Comparator.comparing((FileItem item) -> item.size() == null ? 0L : item.size()).reversed();
            case "modified" -> Comparator.comparing(item -> item.modifiedAt() == null ? Instant.MIN : item.modifiedAt());
            case "modified-desc" -> Comparator.comparing((FileItem item) -> item.modifiedAt() == null ? Instant.MIN : item.modifiedAt()).reversed();
            default -> Comparator.comparing(FileItem::name, String.CASE_INSENSITIVE_ORDER);
        };
        result.sort(comparator);
        return result;
    }

    private boolean matchesType(FileItem item, String type) {
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) return true;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "folder" -> "FOLDER".equals(item.type());
            case "image" -> item.mimeType().startsWith("image/");
            case "pdf" -> "application/pdf".equals(item.mimeType());
            case "file" -> "FILE".equals(item.type());
            default -> true;
        };
    }

    private JsonNode getJson(String url, String accessToken) {
        return sendJson(HttpRequest.newBuilder(URI.create(url))
            .timeout(java.time.Duration.ofSeconds(8))
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .GET()
            .build());
    }

    private JsonNode sendJson(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                JsonNode responseBody = response.body().isBlank()
                    ? null : mapper.readTree(response.body());
                JsonNode errorBody = responseBody == null ? null : responseBody.path("error");
                String code;
                String message;
                if (errorBody != null && errorBody.isObject()) {
                    code = errorBody.path("code").asText("");
                    message = errorBody.path("message").asText("");
                } else {
                    code = responseBody == null ? "" : responseBody.path("error").asText("");
                    message = responseBody == null
                        ? "" : responseBody.path("error_description").asText("");
                }
                String detail = "Microsoft Graph error " + response.statusCode();
                if (!code.isBlank()) detail += " [" + code + "]";
                if (!message.isBlank()) {
                    String safeMessage = message.split("\\r?\\n", 2)[0];
                    detail += ": " + safeMessage;
                }
                throw new GoogleApiException(response.statusCode(), detail);
            }
            if (response.body().isBlank()) return mapper.getNodeFactory().nullNode();
            return mapper.readTree(response.body());
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot call Microsoft Graph", exception);
        }
    }

    private JsonNode postForm(String url, String body) {
        return sendJson(HttpRequest.newBuilder(URI.create(url))
            .timeout(java.time.Duration.ofSeconds(8))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build());
    }

    private void requireConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank())
            throw new IllegalStateException("OneDrive OAuth is not configured");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }
}
