package com.cloudhub.cloud;

import com.cloudhub.auth.AuthController;
import com.cloudhub.auth.User;
import com.cloudhub.auth.UserRepository;
import com.cloudhub.provider.AccountInfo;
import com.cloudhub.provider.CloudProvider;
import com.cloudhub.provider.ProviderRegistry;
import com.cloudhub.provider.ProviderType;
import com.cloudhub.provider.TokenSet;
import com.cloudhub.security.TokenCipher;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final ProviderRegistry providers;
    private final TokenCipher cipher;
    private final SecureRandom random = new SecureRandom();

    public OAuthController(OAuthStateRepository states, OAuthStateService oauthStates, CloudAccountRepository accounts,
                           UserRepository users, ProviderRegistry providers, TokenCipher cipher) {
        this.states = states;
        this.oauthStates = oauthStates;
        this.accounts = accounts;
        this.users = users;
        this.providers = providers;
        this.cipher = cipher;
    }

    @GetMapping("/{provider:google|dropbox|onedrive}/start")
    public void start(@PathVariable String provider, HttpSession session, HttpServletResponse response) throws IOException {
        User user = currentUser(session);
        CloudProvider adapter = providers.get(ProviderType.valueOf(provider.toUpperCase(Locale.ROOT)));
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        String verifier = adapter.createCodeVerifier();
        var url = adapter.buildAuthorizationUrl(state, adapter.createCodeChallenge(verifier));
        states.save(new OAuthState(state, verifier, adapter.type(), user));
        response.sendRedirect(url.toString());
    }

    @GetMapping("/{provider:google|dropbox|onedrive}/callback")
    public void callback(@PathVariable String provider, @RequestParam String code, @RequestParam String state,
                         HttpSession session, HttpServletResponse response) throws IOException {
        User user = currentUser(session);
        ProviderType type = ProviderType.valueOf(provider.toUpperCase(Locale.ROOT));
        CloudProvider adapter = providers.get(type);
        String verifier = oauthStates.consume(state, type, user.getId());
        TokenSet tokens = adapter.exchangeCode(code, verifier);
        AccountInfo info = adapter.getAccountInfo(tokens.accessToken());
        CloudAccount account = accounts.findByProviderAndExternalAccountId(type, info.externalId())
            .orElseGet(() -> new CloudAccount(user, type, info.externalId()));
        if (!user.getId().equals(account.getUser().getId())) {
            throw new IllegalArgumentException("Cloud account is already connected to another user");
        }
        account.setDetails(info.displayName(), info.email(), info.scopes());
        account.setTokens(cipher.encrypt(tokens.accessToken()), cipher.encrypt(tokens.refreshToken()), tokens.expiresAt());
        accounts.save(account);
        response.sendRedirect("http://localhost:5173/dashboard");
    }

    private User currentUser(HttpSession session) {
        Object userId = session.getAttribute(AuthController.SESSION_USER);
        if (userId == null) throw new IllegalArgumentException("Login required");
        return users.findById(UUID.fromString(userId.toString()))
            .orElseThrow(() -> new IllegalArgumentException("Login required"));
    }
}
