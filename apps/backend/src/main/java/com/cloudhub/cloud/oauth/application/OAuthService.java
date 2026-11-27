package com.cloudhub.cloud.oauth.application;

import com.cloudhub.auth.application.AuthService;
import com.cloudhub.auth.persistence.User;
import com.cloudhub.cloud.account.application.CloudAccountService;
import com.cloudhub.cloud.oauth.persistence.OAuthState;
import com.cloudhub.cloud.oauth.persistence.OAuthStateRepository;
import com.cloudhub.provider.CloudProvider;
import com.cloudhub.provider.ProviderRegistry;
import com.cloudhub.provider.ProviderType;
import com.cloudhub.provider.TokenSet;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OAuthService {
    private final OAuthStateRepository states;
    private final OAuthStateService oauthStates;
    private final CloudAccountService accounts;
    private final AuthService auth;
    private final ProviderRegistry providers;
    private final SecureRandom random = new SecureRandom();

    public OAuthService(OAuthStateRepository states, OAuthStateService oauthStates, CloudAccountService accounts,
                        AuthService auth, ProviderRegistry providers) {
        this.states = states;
        this.oauthStates = oauthStates;
        this.accounts = accounts;
        this.auth = auth;
        this.providers = providers;
    }

    public URI start(UUID userId, ProviderType provider) {
        User user = auth.requireUser(userId);
        CloudProvider adapter = providers.get(provider);
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        String verifier = adapter.createCodeVerifier();
        URI url = adapter.buildAuthorizationUrl(state, adapter.createCodeChallenge(verifier));
        states.save(new OAuthState(state, verifier, provider, user));
        return url;
    }

    public void complete(UUID userId, ProviderType provider, String code, String state) {
        User user = auth.requireUser(userId);
        CloudProvider adapter = providers.get(provider);
        String verifier = oauthStates.consume(state, provider, userId);
        TokenSet tokens = adapter.exchangeCode(code, verifier);
        accounts.link(user, provider, adapter.getAccountInfo(tokens.accessToken()), tokens);
    }
}
