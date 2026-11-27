package com.cloudhub.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
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
public class DropboxProvider implements CloudProvider {
    private final String clientId;
    private final String clientSecret;
    private final URI redirectUri;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public DropboxProvider(
        @Value("${dropbox.client-id:}") String clientId,
        @Value("${dropbox.client-secret:}") String clientSecret,
        @Value("${dropbox.redirect-uri}") String redirectUri
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = URI.create(redirectUri);
    }

    @Override public ProviderType type() { return ProviderType.DROPBOX; }
    @Override public Capabilities capabilities() {
        return new Capabilities(true, true, true, true, true, true, true, true, true);
    }

    @Override
    public URI buildAuthorizationUrl(String state, String codeChallenge) {
        requireConfigured();
        String scopes = "account_info.read files.metadata.read files.content.read files.content.write";
        return URI.create("https://www.dropbox.com/oauth2/authorize?"
            + "client_id=" + encode(clientId)
            + "&redirect_uri=" + encode(redirectUri.toString())
            + "&response_type=code"
            + "&token_access_type=offline"
            + "&code_challenge=" + encode(codeChallenge)
            + "&code_challenge_method=S256"
            + "&scope=" + encode(scopes)
            + "&state=" + encode(state));
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
        String body = "code=" + encode(code)
            + "&client_id=" + encode(clientId)
            + "&redirect_uri=" + encode(redirectUri.toString())
            + "&grant_type=authorization_code"
            + "&code_verifier=" + encode(codeVerifier);
        if (!clientSecret.isBlank()) body += "&client_secret=" + encode(clientSecret);
        JsonNode json = postForm("https://api.dropboxapi.com/oauth2/token", body);
        return tokenSet(json);
    }

    @Override
    public TokenSet refresh(String refreshToken) {
        requireConfigured();
        String body = "refresh_token=" + encode(refreshToken)
            + "&grant_type=refresh_token";
        if (!clientSecret.isBlank()) body += "&client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret);
        JsonNode json = postForm("https://api.dropboxapi.com/oauth2/token", body);
        return tokenSet(json);
    }

    private TokenSet tokenSet(JsonNode json) {
        if (!json.has("access_token")) throw new IllegalStateException("Dropbox returned no access token");
        long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 14400;
        String refresh = json.has("refresh_token") ? json.get("refresh_token").asText() : null;
        return new TokenSet(json.get("access_token").asText(), refresh, Instant.now().plusSeconds(expiresIn));
    }

    @Override
    public void revoke(String refreshToken) {
        TokenSet refreshed = refresh(refreshToken);
        postRpcJson("/2/auth/token/revoke", "null", refreshed.accessToken());
    }

    @Override
    public AccountInfo getAccountInfo(String accessToken) {
        JsonNode json = postRpcJson("/2/users/get_current_account", "null", accessToken);
        JsonNode name = json.path("name");
        return new AccountInfo(
            json.path("account_id").asText(),
            name.path("display_name").asText("Dropbox account"),
            json.path("email").asText(),
            "account_info.read files.metadata.read files.content.read files.content.write"
        );
    }

    @Override
    public StorageQuota getQuota(String accessToken) {
        JsonNode json = postRpcJson("/2/users/get_space_usage", "null", accessToken);
        long used = json.path("used").asLong(0);
        JsonNode allocation = json.path("allocation");
        long limit = allocation.path("allocated").asLong(0);
        return new StorageQuota(limit, used, used, 0);
    }

    @Override
    public Page<FileItem> listRoot(String accessToken, String folderId, String pageToken,
                                   String accountId, String type, String sort) {
        JsonNode json;
        if (pageToken == null || pageToken.isBlank()) {
            String path = normalizePath(folderId);
            json = postRpcJson("/2/files/list_folder",
                "{\"path\":\"" + escapeJson(path) + "\",\"recursive\":false,\"limit\":200}",
                accessToken);
        } else {
            json = postRpcJson("/2/files/list_folder/continue",
                "{\"cursor\":\"" + escapeJson(pageToken) + "\"}", accessToken);
        }
        List<FileItem> items = mapEntries(json.path("entries"), accountId);
        items = filterAndSort(items, type, sort);
        return new Page<>(items, json.path("has_more").asBoolean(false)
            ? json.path("cursor").asText(null) : null);
    }

    @Override
    public Page<FileItem> search(String accessToken, String query, String folderId,
                                 String pageToken, String accountId, String type, String sort) {
        JsonNode json;
        if (pageToken == null || pageToken.isBlank()) {
            String body = "{\"query\":\"" + escapeJson(query == null ? "" : query)
                + "\",\"options\":{\"path\":\"" + escapeJson(normalizePath(folderId))
                + "\",\"max_results\":200}}";
            json = postRpcJson("/2/files/search_v2", body, accessToken);
        } else {
            json = postRpcJson("/2/files/search/continue_v2",
                "{\"cursor\":\"" + escapeJson(pageToken) + "\"}", accessToken);
        }
        List<JsonNode> entries = new ArrayList<>();
        for (JsonNode match : json.path("matches")) entries.add(match.path("metadata"));
        List<FileItem> items = mapNodes(entries, accountId);
        items = filterAndSort(items, type, sort);
        return new Page<>(items, json.path("has_more").asBoolean(false)
            ? json.path("cursor").asText(null) : null);
    }

    @Override
    public FileItem getMetadata(String accessToken, String fileId, String accountId) {
        JsonNode json = postRpcJson("/2/files/get_metadata",
            "{\"path\":\"" + escapeJson(fileId) + "\"}", accessToken);
        return mapEntry(json, accountId);
    }

    @Override
    public DownloadStream download(String accessToken, String fileId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://content.dropboxapi.com/2/files/download"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Dropbox-API-Arg", "{\"path\":\"" + escapeJson(fileId) + "\"}")
                .GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) throw new GoogleApiException(response.statusCode(), "Dropbox download failed");
            String filename = response.headers().firstValue("Dropbox-API-Result")
                .map(this::filenameFromMetadataHeader)
                .orElseGet(() -> getMetadata(accessToken, fileId, "").name());
            String mime = response.headers().firstValue("Content-Type").orElse(mimeType(filename));
            long size = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            return new DownloadStream(response.body(), size, mime, filename);
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot download from Dropbox", exception);
        }
    }

    String filenameFromMetadataHeader(String metadataHeader) {
        try {
            String filename = mapper.readTree(metadataHeader).path("name").asText();
            if (filename.isBlank()) {
                throw new IllegalStateException("Dropbox download metadata did not include a filename");
            }
            return filename;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot parse Dropbox download metadata", exception);
        }
    }

    @Override
    public DownloadStream thumbnail(String accessToken, String fileId) {
        try {
            HttpRequest request = thumbnailRequest(accessToken, fileId);
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) throw new GoogleApiException(response.statusCode(), "Dropbox thumbnail failed");
            String mime = response.headers().firstValue("Content-Type").orElse("image/jpeg");
            long size = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            return new DownloadStream(response.body(), size, mime, metadataName(fileId));
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot download Dropbox thumbnail", exception);
        }
    }

    static HttpRequest thumbnailRequest(String accessToken, String fileId) {
        String arg = "{\"resource\":{\".tag\":\"path\",\"path\":\"" + escapeJson(fileId)
            + "\"},\"format\":\"jpeg\",\"size\":\"w128h128\",\"mode\":\"strict\"}";
        return HttpRequest.newBuilder(URI.create("https://content.dropboxapi.com/2/files/get_thumbnail_v2"))
            .header("Authorization", "Bearer " + accessToken)
            .header("Dropbox-API-Arg", arg)
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    }

    @Override
    public FileItem upload(String accessToken, String parentId, String name, String mimeType,
                           InputStream data, long size, String accountId) {
        String safeName = name == null || name.isBlank() ? "Untitled" : name;
        String path = parentId == null || parentId.isBlank() ? "" : normalizePath(parentId);
        if (parentId != null && parentId.startsWith("id:")) {
            JsonNode metadata = postRpcJson("/2/files/get_metadata",
                "{\"path\":\"" + escapeJson(parentId) + "\"}", accessToken);
            String resolvedPath = metadata.path("path_display").asText("");
            if (!resolvedPath.isBlank()) path = resolvedPath;
        }
        path = path.isEmpty() ? "/" + safeName : path + "/" + safeName;
        try {
            JsonNode json;
            if (size <= 150 * 1024 * 1024 && size <= 8 * 1024 * 1024) {
                String arg = "{\"path\":\"" + escapeJson(path)
                    + "\",\"mode\":\"add\",\"autorename\":true,\"mute\":false}";
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://content.dropboxapi.com/2/files/upload"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Dropbox-API-Arg", arg)
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() -> data))
                    .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) throw new GoogleApiException(response.statusCode(), "Dropbox upload failed");
                json = mapper.readTree(response.body());
            } else if (size <= 150 * 1024 * 1024) {
                HttpRequest startRequest = HttpRequest.newBuilder(
                        URI.create("https://content.dropboxapi.com/2/files/upload_session/start"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Dropbox-API-Arg", "{\"close\":false}")
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
                HttpResponse<String> start = http.send(startRequest, HttpResponse.BodyHandlers.ofString());
                if (start.statusCode() >= 400) throw new GoogleApiException(start.statusCode(), "Dropbox upload session could not start");
                String sessionId = mapper.readTree(start.body()).path("session_id").asText();
                if (sessionId.isBlank()) throw new IllegalStateException("Dropbox did not return an upload session");

                byte[] buffer = new byte[4 * 1024 * 1024];
                long offset = 0;
                while (offset < size) {
                    int requested = (int) Math.min((long) buffer.length, size - offset);
                    int read = data.readNBytes(buffer, 0, requested);
                    if (read <= 0) throw new IllegalStateException("Dropbox upload stream ended before the declared size");
                    String appendArg = "{\"cursor\":{\"session_id\":\"" + escapeJson(sessionId)
                        + "\",\"offset\":" + offset + "},\"close\":false}";
                    HttpRequest appendRequest = HttpRequest.newBuilder(
                            URI.create("https://content.dropboxapi.com/2/files/upload_session/append_v2"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Dropbox-API-Arg", appendArg)
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(java.util.Arrays.copyOf(buffer, read)))
                        .build();
                    HttpResponse<String> append = http.send(appendRequest, HttpResponse.BodyHandlers.ofString());
                    if (append.statusCode() >= 400) throw new GoogleApiException(append.statusCode(), "Dropbox upload session append failed");
                    offset += read;
                }

                String finishArg = "{\"cursor\":{\"session_id\":\"" + escapeJson(sessionId)
                    + "\",\"offset\":" + size + "},\"commit\":{\"path\":\"" + escapeJson(path)
                    + "\",\"mode\":\"add\",\"autorename\":true,\"mute\":false}}";
                HttpRequest finishRequest = HttpRequest.newBuilder(
                        URI.create("https://content.dropboxapi.com/2/files/upload_session/finish"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Dropbox-API-Arg", finishArg)
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
                HttpResponse<String> finish = http.send(finishRequest, HttpResponse.BodyHandlers.ofString());
                if (finish.statusCode() >= 400) throw new GoogleApiException(finish.statusCode(), "Dropbox upload session finish failed");
                json = mapper.readTree(finish.body());
            } else {
                throw new IllegalArgumentException("Dropbox upload limit exceeded");
            }
            return mapEntry(json, accountId);
        } catch (GoogleApiException | IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot upload to Dropbox", exception);
        }
    }

    @Override
    public FileItem createFolder(String accessToken, String parentId, String name, String accountId) {
        String path = normalizePath(parentId);
        path = path.isEmpty() ? "/" + (name == null || name.isBlank() ? "Untitled" : name)
            : path + "/" + (name == null || name.isBlank() ? "Untitled" : name);
        JsonNode json = postRpcJson("/2/files/create_folder_v2",
            "{\"path\":\"" + escapeJson(path) + "\",\"autorename\":false}", accessToken);
        return mapEntry(json.path("metadata"), accountId);
    }

    @Override
    public FileItem rename(String accessToken, String fileId, String newName, String accountId) {
        String parent = parentPath(fileId);
        String to = parent.isEmpty() ? "/" + newName : parent + "/" + newName;
        JsonNode json = postRpcJson("/2/files/move_v2",
            "{\"from_path\":\"" + escapeJson(fileId) + "\",\"to_path\":\"" + escapeJson(to)
                + "\",\"autorename\":false}",
            accessToken);
        return mapEntry(json.path("metadata"), accountId);
    }

    @Override
    public void trash(String accessToken, String fileId) {
        postRpc("/2/files/delete_v2",
            "{\"path\":\"" + escapeJson(fileId) + "\"}", accessToken);
    }

    private List<FileItem> mapEntries(JsonNode entries, String accountId) {
        List<FileItem> items = new ArrayList<>();
        for (JsonNode entry : entries) items.add(mapEntry(entry, accountId));
        return items;
    }

    private List<FileItem> mapNodes(List<JsonNode> nodes, String accountId) {
        List<FileItem> items = new ArrayList<>();
        for (JsonNode entry : nodes) items.add(mapEntry(entry, accountId));
        return items;
    }

    private FileItem mapEntry(JsonNode entry, String accountId) {
        String tag = entry.path(".tag").asText("");
        boolean folder = "folder".equals(tag);
        String name = entry.path("name").asText("Untitled");
        String mime = folder ? "application/vnd.dropbox.folder" : mimeType(name);
        String id = entry.path("id").asText();
        String path = entry.path("path_display").asText("");
        String parent = parentPath(path);
        Instant modified = null;
        if (entry.has("server_modified") && !entry.path("server_modified").isNull()) {
            try { modified = Instant.parse(entry.path("server_modified").asText()); } catch (Exception ignored) {}
        }
        Long size = entry.has("size") && !entry.path("size").isNull()
            ? entry.path("size").asLong() : null;
        boolean previewable = mime.startsWith("image/") || "application/pdf".equals(mime);
        return new FileItem(id, accountId, ProviderType.DROPBOX, name,
            folder ? "FOLDER" : "FILE", mime, size, modified, parent,
            false, null, false, supportsThumbnail(name) ? "/thumbnail" : null, previewable);
    }

    static boolean supportsThumbnail(String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        int dot = lowerName.lastIndexOf('.');
        if (dot < 0) return false;
        return switch (lowerName.substring(dot + 1)) {
            case "jpg", "jpeg", "png", "tiff", "tif", "gif", "webp", "ppm", "bmp" -> true;
            default -> false;
        };
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

    private String normalizePath(String folderId) {
        if (folderId == null || folderId.isBlank()) return "";
        if (folderId.startsWith("id:")) return folderId;
        return folderId.startsWith("/") ? folderId : "/" + folderId;
    }

    private static String parentPath(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "" : path.substring(0, slash);
    }

    private static String metadataName(String path) {
        if (path == null) return "download";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String mimeType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.matches(".*\\.(png|jpg|jpeg|gif|webp|bmp)$")) return "image/" + lower.substring(lower.lastIndexOf('.') + 1).replace("jpg", "jpeg");
        return "application/octet-stream";
    }

    private JsonNode postRpcJson(String endpoint, String body, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.dropboxapi.com" + endpoint))
            .timeout(java.time.Duration.ofSeconds(8))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return send(request);
    }

    private void postRpc(String endpoint, String body, String accessToken) {
        postRpcJson(endpoint, body, accessToken);
    }

    private JsonNode postForm(String url, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(java.time.Duration.ofSeconds(8))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new GoogleApiException(response.statusCode(), "Dropbox API error " + response.statusCode());
            }
            if (response.body().isBlank()) return mapper.getNodeFactory().nullNode();
            return mapper.readTree(response.body());
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot call Dropbox API", exception);
        }
    }

    private void requireConfigured() {
        if (clientId.isBlank()) throw new IllegalStateException("Dropbox OAuth is not configured");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
