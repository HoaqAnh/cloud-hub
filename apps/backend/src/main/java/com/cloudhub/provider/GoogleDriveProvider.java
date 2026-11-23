package com.cloudhub.provider;

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
    public Page<FileItem> listRoot(String accessToken, String folderId, String pageToken, String accountId) {
        String parent = folderId == null || folderId.isBlank() ? "root" : folderId;
        String q = "'" + parent.replace("'", "\\'") + "' in parents and trashed=false";
        String fields = "nextPageToken,files(id,name,mimeType,size,modifiedTime,parents,shortcutDetails)";
        String url = "https://www.googleapis.com/drive/v3/files?q=" + encode(q)
            + "&fields=" + encode(fields)
            + "&pageSize=100"
            + "&orderBy=folder,name";
        if (pageToken != null && !pageToken.isBlank()) url += "&pageToken=" + encode(pageToken);

        JsonNode json = getJson(url, accessToken);
        List<FileItem> items = new ArrayList<>();
        for (JsonNode file : json.path("files")) {
            String mime = file.path("mimeType").asText("");
            boolean folder = "application/vnd.google-apps.folder".equals(mime);
            boolean shortcut = "application/vnd.google-apps.shortcut".equals(mime);
            boolean nativeDoc = mime.startsWith("application/vnd.google-apps.");
            String parentId = file.path("parents").isArray() && file.path("parents").size() > 0
                ? file.path("parents").get(0).asText() : null;
            String shortcutTarget = shortcut
                ? file.path("shortcutDetails").path("targetId").asText(null) : null;
            Instant modified = file.has("modifiedTime")
                ? Instant.parse(file.get("modifiedTime").asText()) : null;
            Long size = file.has("size") ? file.get("size").asLong() : null;
            items.add(new FileItem(
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
                nativeDoc
            ));
        }
        return new Page<>(items, json.path("nextPageToken").asText(null));
    }

    private JsonNode getJson(String url, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .GET().build();
        return send(request);
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
                throw new IllegalStateException("Google API error " + response.statusCode());
            }
            return mapper.readTree(response.body());
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
