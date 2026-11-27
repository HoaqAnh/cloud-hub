package com.cloudhub.cloud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudhub.auth.persistence.User;
import com.cloudhub.auth.persistence.UserRepository;
import com.cloudhub.cloud.account.application.AccessTokenService;
import com.cloudhub.cloud.account.persistence.CloudAccount;
import com.cloudhub.cloud.account.persistence.CloudAccountRepository;
import com.cloudhub.cloud.oauth.application.OAuthStateService;
import com.cloudhub.cloud.oauth.persistence.OAuthState;
import com.cloudhub.cloud.oauth.persistence.OAuthStateRepository;
import com.cloudhub.cloud.transfer.persistence.PendingMove;
import com.cloudhub.cloud.transfer.persistence.PendingMoveRepository;
import com.cloudhub.provider.AccountInfo;
import com.cloudhub.provider.DownloadStream;
import com.cloudhub.provider.Page;
import com.cloudhub.provider.GoogleApiException;
import com.cloudhub.provider.google.GoogleDriveProvider;
import com.cloudhub.provider.ProviderType;
import com.cloudhub.provider.TokenSet;
import com.cloudhub.security.SessionIdentity;
import com.cloudhub.security.TokenCipher;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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
    @Autowired PendingMoveRepository moves;
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
        var session = session(user);
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
    void preservesAccountAndSearchResponseShapes() throws Exception {
        User owner = user();
        CloudAccount source = account(owner, "search-access", null, Instant.now().plusSeconds(3600));
        var session = session(owner);
        doReturn(new Page<>(List.of(), null)).when(google)
            .listRoot("search-access", null, null, source.getId().toString(), "all", "name");
        doReturn(new Page<>(List.of(), null)).when(google)
            .search("search-access", "report", null, null, source.getId().toString(), "all", "name");

        mvc.perform(get("/api/accounts").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(source.getId().toString()))
            .andExpect(jsonPath("$[0].provider").value("GOOGLE"))
            .andExpect(jsonPath("$[0].displayName").value("Google Drive"))
            .andExpect(jsonPath("$[0].email").value(""))
            .andExpect(jsonPath("$[0].capabilities.download").value(true));
        mvc.perform(get("/api/accounts/{id}/files", source.getId()).session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nextPageToken").value(""));
        mvc.perform(get("/api/search").param("q", "report").param("accountId", source.getId().toString())
                .session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.nextPageToken").value(""));
        mvc.perform(get("/api/search").param("q", "report").param("providers", "google").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.q").value("report"))
            .andExpect(jsonPath("$.groups[0].accountName").value("GOOGLE"))
            .andExpect(jsonPath("$.groups[0].status").value("ok"))
            .andExpect(jsonPath("$.groups[0].nextPageToken").value(""))
            .andExpect(jsonPath("$.groups[0].error").doesNotHaveJsonPath());
    }

    @Test
    void streamsDownloadAndThumbnailWithExistingHeaders() throws Exception {
        User owner = user();
        CloudAccount source = account(owner, "stream-access", null, Instant.now().plusSeconds(3600));
        var session = session(owner);
        byte[] bytes = "file content".getBytes(StandardCharsets.UTF_8);
        doReturn(new DownloadStream(new ByteArrayInputStream(bytes), bytes.length, "text/plain", "tệp gốc.txt"))
            .when(google).download("stream-access", "file");
        doReturn(new DownloadStream(new ByteArrayInputStream(bytes), -1, "image/png", "thumbnail.png"))
            .when(google).thumbnail("stream-access", "file");

        var download = mvc.perform(get("/api/accounts/{id}/files/file/content", source.getId())
                .param("disposition", "inline").session(session))
            .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(download)).andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "inline; filename*=UTF-8''t%E1%BB%87p%20g%E1%BB%91c.txt"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Length", Integer.toString(bytes.length)))
            .andExpect(content().contentType("text/plain")).andExpect(content().bytes(bytes));

        var thumbnail = mvc.perform(get("/api/accounts/{id}/files/file/thumbnail", source.getId()).session(session))
            .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(thumbnail)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "private, max-age=300"))
            .andExpect(header().doesNotExist("Content-Length"))
            .andExpect(content().contentType("image/png")).andExpect(content().bytes(bytes));
    }

    @Test
    void rejectsIncompleteTransferRequestsWithTheExistingError() throws Exception {
        mvc.perform(post("/api/transfers").session(session(user())).with(csrf()).contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.detail").value("Source and destination account/file are required"));
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
    void moveRetryChecksOwnershipAndReplaysOnlyTheSourceStep() throws Exception {
        User owner = user();
        CloudAccount source = account(owner, "source-access", null, Instant.now().plusSeconds(3600));
        UUID id = moves.save(new PendingMove(owner.getId(), source.getId(), "source-file")).id();
        org.mockito.Mockito.doNothing().when(google).trash("source-access", "source-file");

        String route = "/api/transfers/{retryId}/retry-source";
        mvc.perform(post(route, id).with(csrf()).session(session(user()))).andExpect(status().isNotFound());
        var session = session(owner);
        mvc.perform(post(route, id).with(csrf()).session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.moved").value(true));
        mvc.perform(post(route, id).with(csrf()).session(session)).andExpect(status().isOk());
        verify(google).trash("source-access", "source-file");
    }

    private MockHttpSession session(User user) {
        var session = new MockHttpSession();
        session.setAttribute(SessionIdentity.SESSION_USER, user.getId().toString());
        return session;
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
