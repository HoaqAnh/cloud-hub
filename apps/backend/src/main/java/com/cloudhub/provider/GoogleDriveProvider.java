package com.cloudhub.provider;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleDriveProvider implements CloudProvider {
    private final String clientId;
    private final String clientSecret;
    private final URI redirectUri;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public GoogleDriveProvider(
        @Value("${google.client-id:}") String clientId,
        @Value("${google.client-secret:}") String clientSecret,
        @Value("${google.redirect-uri}") String redirectUri
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = URI.create(redirectUri);
    }

    @Override public ProviderType type() { return ProviderType.GOOGLE; }
    @Override public Capabilities capabilities() {
        return new Capabilities(true, true, true, true, true);
    }

    @Override
    public URI buildAuthorizationUrl(String state, String codeChallenge) {
        requireConfigured();
        String scopes = "openid email profile https://www.googleapis.com/auth/drive";
        String query = "client_id=" + encode(clientId)
            + "&redirect_uri=" + encode(redirectUri.toString())
            + "&response_type=code"
            + "&scope=" + encode(scopes)
            + "&state=" + encode(state)
            + "&code_challenge=" + encode(codeChallenge)
            + "&code_challenge_method=S256"
            + "&access_type=offline"
            + "&prompt=consent";
        return URI.create("https://accounts.google.com/o/oauth2/v2/auth?" + query);
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
            + "&client_secret=" + encode(clientSecret)
            + "&redirect_uri=" + encode(redirectUri.toString())
            + "&grant_type=authorization_code"
            + "&code_verifier=" + encode(codeVerifier);
        JsonNode json = postForm("https://oauth2.googleapis.com/token", body);
        return tokenSet(json);
    }

    @Override
    public TokenSet refresh(String refreshToken) {
        requireConfigured();
        String body = "refresh_token=" + encode(refreshToken)
            + "&client_id=" + encode(clientId)
            + "&client_secret=" + encode(clientSecret)
            + "&grant_type=refresh_token";
        JsonNode json = postForm("https://oauth2.googleapis.com/token", body);
        return tokenSet(json);
    }

    @Override
    public void revoke(String refreshToken) {
        requireConfigured();
        String body = "token=" + encode(refreshToken);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/revoke"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        send(request);
    }

    private TokenSet tokenSet(JsonNode json) {
        if (!json.has("access_token")) throw new IllegalStateException("Google returned no access token");
        long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 3600;
        String refresh = json.has("refresh_token") ? json.get("refresh_token").asText() : null;
        return new TokenSet(json.get("access_token").asText(), refresh, Instant.now().plusSeconds(expiresIn));
    }

    @Override
    public AccountInfo getAccountInfo(String accessToken) {
        JsonNode json = getJson("https://www.googleapis.com/oauth2/v2/userinfo", accessToken);
        return new AccountInfo(
            json.path("id").asText(),
            json.path("name").asText(json.path("email").asText("Google account")),
            json.path("email").asText(),
            "openid email profile drive"
        );
    }

    @Override
    public StorageQuota getQuota(String accessToken) {
        JsonNode json = getJson(
            "https://www.googleapis.com/drive/v3/about?fields=storageQuota",
            accessToken
        ).path("storageQuota");
        return new StorageQuota(
            json.path("limit").asLong(0),
            json.path("usage").asLong(0),
            json.path("usageInDrive").asLong(0),
            json.path("usageInTrash").asLong(0)
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
        String parent = folderId == null || folderId.isBlank() ? "root" : folderId;
        String q = "'" + parent.replace("'", "\\'") + "' in parents and trashed=false";
        q = addTypeFilter(q, type);
        String fields = "nextPageToken,files(id,name,mimeType,size,modifiedTime,parents,shortcutDetails,thumbnailLink)";
        String url = "https://www.googleapis.com/drive/v3/files?q=" + encode(q)
            + "&fields=" + encode(fields)
            + "&pageSize=100"
            + "&orderBy=" + encodeOrder(sort);
        if (pageToken != null && !pageToken.isBlank()) url += "&pageToken=" + encode(pageToken);

        JsonNode json = getJson(url, accessToken);
        List<FileItem> items = new ArrayList<>();
        for (JsonNode file : json.path("files")) {
            items.add(mapFile(file, accountId));
        }
        return new Page<>(items, json.path("nextPageToken").asText(null));
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
        String q = "trashed=false";
        if (query != null && !query.isBlank()) {
            q += " and name contains '" + query.replace("'", "\\'") + "'";
        }
        if (folderId != null && !folderId.isBlank()) {
            q += " and '" + folderId.replace("'", "\\'") + "' in parents";
        }
        q = addTypeFilter(q, type);
        String fields = "nextPageToken,files(id,name,mimeType,size,modifiedTime,parents,shortcutDetails,thumbnailLink)";
        String url = "https://www.googleapis.com/drive/v3/files?q=" + encode(q)
            + "&fields=" + encode(fields)
            + "&pageSize=100"
            + "&orderBy=" + encodeOrder(sort);
        if (pageToken != null && !pageToken.isBlank()) url += "&pageToken=" + encode(pageToken);
        JsonNode json = getJson(url, accessToken);
        List<FileItem> items = new ArrayList<>();
        for (JsonNode file : json.path("files")) items.add(mapFile(file, accountId));
        return new Page<>(items, json.path("nextPageToken").asText(null));
    }

    private JsonNode getJson(String url, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .GET().build();
        return send(request);
    }

    @Override
    public FileItem getMetadata(String accessToken, String fileId, String accountId) {
        String fields = "id,name,mimeType,size,modifiedTime,parents,shortcutDetails,thumbnailLink";
        JsonNode file = getJson(
            "https://www.googleapis.com/drive/v3/files/" + encodePath(fileId)
                + "?fields=" + encode(fields),
            accessToken
        );
        return mapFile(file, accountId);
    }

    @Override
    public DownloadStream download(String accessToken, String fileId) {
        FileItem metadata = getMetadata(accessToken, fileId, "");
        String mimeType = metadata.mimeType();
        String filename = metadata.name();
        String url;

        if (metadata.nativeDoc()) {
            String exportMime = switch (mimeType) {
                case "application/vnd.google-apps.spreadsheet" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "application/vnd.google-apps.presentation" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                default -> "application/pdf";
            };
            url = "https://www.googleapis.com/drive/v3/files/" + encodePath(fileId)
                + "/export?mimeType=" + encode(exportMime);
            mimeType = exportMime;
            filename = exportFilename(filename, exportMime);
        } else {
            url = "https://www.googleapis.com/drive/v3/files/" + encodePath(fileId) + "?alt=media";
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "*/*")
            .GET().build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new GoogleApiException(response.statusCode(), "Google Drive download failed");
            }
            long size = response.headers().firstValueAsLong("Content-Length").orElse(metadata.size() == null ? -1 : metadata.size());
            return new DownloadStream(response.body(), size, mimeType, filename);
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot download file from Google Drive", exception);
        }
    }

    @Override
    public DownloadStream thumbnail(String accessToken, String fileId) {
        JsonNode file = getJson(
            "https://www.googleapis.com/drive/v3/files/" + encodePath(fileId)
                + "?fields=id,name,mimeType,thumbnailLink",
            accessToken
        );
        String link = file.path("thumbnailLink").asText("");
        if (link.isBlank()) throw new IllegalStateException("Thumbnail is not available");
        HttpRequest request = HttpRequest.newBuilder(URI.create(link))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new GoogleApiException(response.statusCode(), "Google Drive thumbnail failed");
            }
            String mime = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            long size = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            return new DownloadStream(response.body(), size, mime, file.path("name").asText("thumbnail"));
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot download thumbnail from Google Drive", exception);
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
        String parent = parentId == null || parentId.isBlank() ? "root" : parentId;
        String safeName = name == null || name.isBlank() ? "Untitled" : name;
        String safeMimeType = mimeType == null || mimeType.isBlank()
            ? "application/octet-stream" : mimeType;
        HttpRequest.BodyPublisher uploadBody = uploadBody(data, size);
        try {
            String metadataJson = mapper.writeValueAsString(
                java.util.Map.of("name", safeName, "parents", List.of(parent), "mimeType", safeMimeType)
            );
            HttpRequest initiate = HttpRequest.newBuilder(
                    URI.create("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name,mimeType,size,modifiedTime,parents"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type", safeMimeType)
                .header("X-Upload-Content-Length", Long.toString(size))
                .POST(HttpRequest.BodyPublishers.ofString(metadataJson))
                .build();
            HttpResponse<Void> initiated = http.send(initiate, HttpResponse.BodyHandlers.discarding());
            if (initiated.statusCode() >= 400) {
                throw new GoogleApiException(initiated.statusCode(), "Google Drive upload could not be started");
            }
            String location = initiated.headers().firstValue("Location")
                .orElseThrow(() -> new IllegalStateException("Google Drive did not return a resumable upload URL"));

            HttpRequest upload = HttpRequest.newBuilder(URI.create(location))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", safeMimeType)
                .PUT(uploadBody)
                .build();
            HttpResponse<String> uploaded = http.send(upload, HttpResponse.BodyHandlers.ofString());
            if (uploaded.statusCode() >= 400) {
                throw new GoogleApiException(uploaded.statusCode(), "Google Drive upload failed");
            }
            JsonNode file = mapper.readTree(uploaded.body());
            return mapFile(file, accountId);
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot upload file to Google Drive", exception);
        }
    }

    @Override
    public FileItem createFolder(String accessToken, String parentId, String name, String accountId) {
        String parent = parentId == null || parentId.isBlank() ? "root" : parentId;
        String safeName = name == null || name.isBlank() ? "Untitled" : name;
        try {
            String metadataJson = mapper.writeValueAsString(
                java.util.Map.of("name", safeName, "parents", List.of(parent),
                    "mimeType", "application/vnd.google-apps.folder")
            );
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("https://www.googleapis.com/drive/v3/files?fields=id,name,mimeType,size,modifiedTime,parents,shortcutDetails,thumbnailLink"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(metadataJson))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new GoogleApiException(response.statusCode(), "Could not create folder");
            }
            return mapFile(mapper.readTree(response.body()), accountId);
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create folder in Google Drive", exception);
        }
    }

    @Override
    public FileItem rename(String accessToken, String fileId, String newName, String accountId) {
        String safeName = newName == null || newName.isBlank() ? "Untitled" : newName;
        try {
            String body = mapper.writeValueAsString(java.util.Map.of("name", safeName));
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("https://www.googleapis.com/drive/v3/files/" + encodePath(fileId)
                        + "?fields=id,name,mimeType,size,modifiedTime,parents,shortcutDetails,thumbnailLink"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new GoogleApiException(response.statusCode(), "Could not rename file");
            }
            return mapFile(mapper.readTree(response.body()), accountId);
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot rename file in Google Drive", exception);
        }
    }

    @Override
    public void trash(String accessToken, String fileId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("https://www.googleapis.com/drive/v3/files/" + encodePath(fileId)))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"trashed\":true}"))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new GoogleApiException(response.statusCode(), "Could not move file to trash");
            }
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot trash file in Google Drive", exception);
        }
    }

    static HttpRequest.BodyPublisher uploadBody(InputStream data, long size) {
        if (size < 0) throw new IllegalArgumentException("File size cannot be negative");
        if (size == 0) return HttpRequest.BodyPublishers.noBody();
        return HttpRequest.BodyPublishers.fromPublisher(
            HttpRequest.BodyPublishers.ofInputStream(() -> data),
            size
        );
    }

    private FileItem mapFile(JsonNode file, String accountId) {
        String mime = file.path("mimeType").asText("");
        boolean folder = "application/vnd.google-apps.folder".equals(mime);
        boolean shortcut = "application/vnd.google-apps.shortcut".equals(mime);
        boolean nativeDoc = mime.startsWith("application/vnd.google-apps.")
            && !folder && !shortcut;
        boolean previewable = mime.startsWith("image/")
            || "application/pdf".equals(mime);
        String thumbnailUrl = file.path("thumbnailLink").asText("");
        String parentId = file.path("parents").isArray() && file.path("parents").size() > 0
            ? file.path("parents").get(0).asText() : null;
        String shortcutTarget = shortcut
            ? file.path("shortcutDetails").path("targetId").asText(null) : null;
        Instant modified = file.has("modifiedTime") && !file.get("modifiedTime").isNull()
            ? Instant.parse(file.get("modifiedTime").asText()) : null;
        Long size = file.has("size") && !file.get("size").isNull()
            ? file.get("size").asLong() : null;
        return new FileItem(
            file.get("id").asText(),
            accountId,
            ProviderType.GOOGLE,
            file.path("name").asText("Untitled"),
            folder ? "FOLDER" : "FILE",
            mime,
            size,
            modified,
            parentId,
            shortcut,
            shortcutTarget,
            nativeDoc,
            thumbnailUrl.isBlank() ? null : thumbnailUrl,
            previewable
        );
    }

    private static String addTypeFilter(String query, String type) {
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) return query;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "folder" -> query + " and mimeType='application/vnd.google-apps.folder'";
            case "image" -> query + " and mimeType contains 'image/'";
            case "pdf" -> query + " and mimeType='application/pdf'";
            case "file" -> query + " and mimeType!='application/vnd.google-apps.folder'";
            default -> query;
        };
    }

    private static String encodeOrder(String sort) {
        if (sort == null) return "folder,name";
        return switch (sort) {
            case "name-desc" -> "folder,name desc";
            case "size" -> "folder,quotaBytesUsed";
            case "size-desc" -> "folder,quotaBytesUsed desc";
            case "modified" -> "folder,modifiedTime";
            case "modified-desc" -> "folder,modifiedTime desc";
            default -> "folder,name";
        };
    }

    private static String encodePath(String value) {
        return value.replace("/", "%2F");
    }

    private static String exportFilename(String name, String exportMime) {
        String base = name.replaceAll("\\.[^.]+$", "");
        if (exportMime.endsWith(".sheet")) return base + ".xlsx";
        if (exportMime.endsWith(".presentation")) return base + ".pptx";
        return base + ".pdf";
    }

    private JsonNode postForm(String url, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
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
                throw new GoogleApiException(response.statusCode(), "Google API error " + response.statusCode());
            }
            if (response.body().isBlank()) return mapper.getNodeFactory().nullNode();
            return mapper.readTree(response.body());
        } catch (GoogleApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot call Google API", exception);
        }
    }

    private void requireConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth is not configured");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
