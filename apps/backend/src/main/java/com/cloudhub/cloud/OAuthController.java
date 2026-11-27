package com.cloudhub.cloud;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import com.cloudhub.auth.AuthController;
import com.cloudhub.auth.User;
import com.cloudhub.auth.UserRepository;
import com.cloudhub.provider.AccountInfo;
import com.cloudhub.provider.GoogleDriveProvider;
import com.cloudhub.provider.DropboxProvider;
import com.cloudhub.provider.OneDriveProvider;
import com.cloudhub.provider.ProviderType;
import com.cloudhub.provider.TokenSet;
import com.cloudhub.security.TokenCipher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oauth")
public class OAuthController {
    private final OAuthStateRepository states;
    private final OAuthStateService oauthStates;
    private final CloudAccountRepository accounts;
    private final UserRepository users;
    private final GoogleDriveProvider google;
    private final DropboxProvider dropbox;
    private final OneDriveProvider onedrive;
    private final TokenCipher cipher;

    public OAuthController(
        OAuthStateRepository states,
        OAuthStateService oauthStates,
        CloudAccountRepository accounts,
        UserRepository users,
        GoogleDriveProvider google,
        DropboxProvider dropbox,
        OneDriveProvider onedrive,
        TokenCipher cipher
    ) {
        this.states = states;
        this.oauthStates = oauthStates;
        this.accounts = accounts;
        this.users = users;
        this.google = google;
        this.dropbox = dropbox;
        this.onedrive = onedrive;
        this.cipher = cipher;
    }

    @GetMapping("/google/start")
    public void start(HttpSession session, HttpServletResponse response) throws IOException {
        User user = currentUser(session);
        String state = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(randomBytes(32));
        String verifier = google.createCodeVerifier();
        states.save(new OAuthState(state, verifier, ProviderType.GOOGLE, user));
        response.sendRedirect(google.buildAuthorizationUrl(state, google.createCodeChallenge(verifier)).toString());
    }

    @GetMapping("/dropbox/start")
    public void startDropbox(HttpSession session, HttpServletResponse response) throws IOException {
        User user = currentUser(session);
        String state = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(randomBytes(32));
        String verifier = dropbox.createCodeVerifier();
        states.save(new OAuthState(state, verifier, ProviderType.DROPBOX, user));
        response.sendRedirect(dropbox.buildAuthorizationUrl(state, dropbox.createCodeChallenge(verifier)).toString());
    }

    @GetMapping("/onedrive/start")
    public void startOneDrive(HttpSession session, HttpServletResponse response) throws IOException {
        User user = currentUser(session);
        String state = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(randomBytes(32));
        String verifier = onedrive.createCodeVerifier();
        states.save(new OAuthState(state, verifier, ProviderType.ONEDRIVE, user));
        response.sendRedirect(onedrive.buildAuthorizationUrl(state, onedrive.createCodeChallenge(verifier)).toString());
    }

    @GetMapping("/google/callback")
    @Transactional
    public void callback(
        @RequestParam String code,
        @RequestParam String state,
        HttpSession session,
        HttpServletResponse response
    ) throws IOException {
        User user = currentUser(session);
        String verifier = oauthStates.consume(state, ProviderType.GOOGLE, user.getId());
        TokenSet tokens = google.exchangeCode(code, verifier);
        AccountInfo info = google.getAccountInfo(tokens.accessToken());
        CloudAccount account = accounts
            .findByProviderAndExternalAccountId(ProviderType.GOOGLE, info.externalId())
            .orElseGet(() -> new CloudAccount(user, ProviderType.GOOGLE, info.externalId()));
        account.setDetails(info.displayName(), info.email(), info.scopes());
        account.setTokens(
            cipher.encrypt(tokens.accessToken()),
            cipher.encrypt(tokens.refreshToken()),
            tokens.expiresAt()
        );
        accounts.save(account);
        response.sendRedirect("http://localhost:5173/dashboard");
    }

    @GetMapping("/dropbox/callback")
    @Transactional
    public void callbackDropbox(
        @RequestParam String code,
        @RequestParam String state,
        HttpSession session,
        HttpServletResponse response
    ) throws IOException {
        User user = currentUser(session);
        String verifier = oauthStates.consume(state, ProviderType.DROPBOX, user.getId());
        TokenSet tokens = dropbox.exchangeCode(code, verifier);
        AccountInfo info = dropbox.getAccountInfo(tokens.accessToken());
        CloudAccount account = accounts
            .findByProviderAndExternalAccountId(ProviderType.DROPBOX, info.externalId())
            .orElseGet(() -> new CloudAccount(user, ProviderType.DROPBOX, info.externalId()));
        account.setDetails(info.displayName(), info.email(), info.scopes());
        account.setTokens(
            cipher.encrypt(tokens.accessToken()),
            cipher.encrypt(tokens.refreshToken()),
            tokens.expiresAt()
        );
        accounts.save(account);
        response.sendRedirect("http://localhost:5173/dashboard");
    }

    @GetMapping("/onedrive/callback")
    @Transactional
    public void callbackOneDrive(
        @RequestParam String code,
        @RequestParam String state,
        HttpSession session,
        HttpServletResponse response
    ) throws IOException {
        User user = currentUser(session);
        String verifier = oauthStates.consume(state, ProviderType.ONEDRIVE, user.getId());
        TokenSet tokens = onedrive.exchangeCode(code, verifier);
        AccountInfo info = onedrive.getAccountInfo(tokens.accessToken());
        CloudAccount account = accounts
            .findByProviderAndExternalAccountId(ProviderType.ONEDRIVE, info.externalId())
            .orElseGet(() -> new CloudAccount(user, ProviderType.ONEDRIVE, info.externalId()));
        account.setDetails(info.displayName(), info.email(), info.scopes());
        account.setTokens(
            cipher.encrypt(tokens.accessToken()),
            cipher.encrypt(tokens.refreshToken()),
            tokens.expiresAt()
        );
        accounts.save(account);
        response.sendRedirect("http://localhost:5173/dashboard");
    }

    private User currentUser(HttpSession session) {
        Object userId = session.getAttribute(AuthController.SESSION_USER);
        if (userId == null) throw new IllegalArgumentException("Login required");
        return users.findById(UUID.fromString(userId.toString()))
            .orElseThrow(() -> new IllegalArgumentException("Login required"));
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
