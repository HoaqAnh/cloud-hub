package com.cloudhub.cloud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudhub.auth.AuthController;
import com.cloudhub.auth.User;
import com.cloudhub.auth.UserRepository;
import com.cloudhub.provider.AccountInfo;
import com.cloudhub.provider.GoogleApiException;
import com.cloudhub.provider.GoogleDriveProvider;
import com.cloudhub.provider.ProviderType;
import com.cloudhub.provider.TokenSet;
import com.cloudhub.security.TokenCipher;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = { "cloudhub.admin.username=admin", "cloudhub.admin.password=test-admin-password" })
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CloudOperationsTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CloudAccountRepository accounts;
    @Autowired OAuthStateRepository states;
    @Autowired OAuthStateService oauthStates;
    @Autowired AccessTokenService tokens;
    @Autowired TokenCipher cipher;
    @Autowired MoveCompletionService moves;
    @MockitoSpyBean GoogleDriveProvider google;

    @Test
    void protectsPrivateRoutesAndRequiresCsrfForLogin() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/oauth/google/start")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"username\":\"admin\",\"password\":\"test-admin-password\"}"))
            .andExpect(status().isForbidden());
        var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content("{\"username\":\"admin\",\"password\":\"test-admin-password\"}"))
            .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/accounts").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void oauthCallbackRejectsWrongProviderAndConsumesAValidStateOnce() throws Exception {
        User user = user();
        String state = UUID.randomUUID().toString();
        states.save(new OAuthState(state, "verifier", ProviderType.GOOGLE, user));
        var session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER, user.getId().toString());
        String externalId = UUID.randomUUID().toString();
        doReturn(new TokenSet("access", "refresh", Instant.now().plusSeconds(3600)))
            .when(google).exchangeCode("fake-code", "verifier");
        doReturn(new AccountInfo(externalId, "drive", "", "")).when(google).getAccountInfo("access");

        mvc.perform(get("/api/oauth/dropbox/callback").session(session).param("code", "fake-code").param("state", state))
            .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> oauthStates.consume(state, ProviderType.GOOGLE, user().getId()))
            .isInstanceOf(IllegalArgumentException.class);
        mvc.perform(get("/api/oauth/google/callback").session(session).param("code", "fake-code").param("state", state))
            .andExpect(status().isFound()).andExpect(header().string("Location", "http://localhost:5173/dashboard"));
        mvc.perform(get("/api/oauth/google/callback").session(session).param("code", "fake-code").param("state", state))
            .andExpect(status().isBadRequest());
        verify(google).exchangeCode("fake-code", "verifier");
    }

    @Test
    void concurrentRefreshUsesFreshTokenAndPreservesRefreshToken() throws Exception {
        CloudAccount account = account(user(), "before-refresh", "shared-refresh", Instant.now().minusSeconds(5));
        doReturn(new TokenSet("new-access", null, Instant.now().plusSeconds(3600)))
            .when(google).refresh("shared-refresh");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> tokens.accessToken(account.getId()));
            var second = executor.submit(() -> tokens.accessToken(account.getId()));
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("new-access");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("new-access");
        }
        assertThat(tokens.accessToken(account.getId())).isEqualTo("new-access");
        verify(google, times(1)).refresh("shared-refresh");
        assertThat(cipher.decrypt(accounts.findById(account.getId()).orElseThrow().getRefreshTokenEnc()))
            .isEqualTo("shared-refresh");
    }

    @Test
    void terminalRefreshFailurePersistsReconnectStatus() {
        CloudAccount account = account(user(), "expired-access", "revoked-refresh", Instant.now().minusSeconds(5));
        doThrow(new GoogleApiException(400, "Invalid grant")).when(google).refresh("revoked-refresh");
        assertThatThrownBy(() -> tokens.accessToken(account.getId()))
            .isInstanceOf(GoogleApiException.class).hasMessage("Account needs reconnect");
        assertThat(accounts.findById(account.getId()).orElseThrow().getStatus()).isEqualTo("NEEDS_RECONNECT");
    }

    @Test
    void moveRetryChecksOwnershipAndReplaysOnlyTheSourceStep() {
        User owner = user();
        CloudAccount source = account(owner, "source-access", null, Instant.now().plusSeconds(3600));
        UUID id = moves.rememberFailure(owner.getId(), source.getId(), "source-file");
        org.mockito.Mockito.doNothing().when(google).trash("source-access", "source-file");

        assertThatThrownBy(() -> moves.retry(id, user().getId())).isInstanceOf(ResponseStatusException.class);
        moves.retry(id, owner.getId());
        moves.retry(id, owner.getId());
        verify(google).trash("source-access", "source-file");
    }

    private User user() {
        UUID id = UUID.randomUUID();
        return users.save(new User(id, id.toString(), "unused"));
    }

    private CloudAccount account(User user, String access, String refresh, Instant expires) {
        CloudAccount account = new CloudAccount(user, ProviderType.GOOGLE, UUID.randomUUID().toString());
        account.setTokens(cipher.encrypt(access), cipher.encrypt(refresh), expires);
        return accounts.save(account);
    }
}
