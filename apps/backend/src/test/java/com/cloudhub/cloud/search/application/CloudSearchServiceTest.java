package com.cloudhub.cloud.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudhub.cloud.account.application.AccessTokenService;
import com.cloudhub.cloud.account.application.CloudAccountService;
import com.cloudhub.cloud.account.application.CloudAccountService.AccountReference;
import com.cloudhub.provider.CloudProvider;
import com.cloudhub.provider.Page;
import com.cloudhub.provider.ProviderRegistry;
import com.cloudhub.provider.ProviderType;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CloudSearchServiceTest {
    private final CloudProvider provider = mock(CloudProvider.class);
    private final CloudAccountService accounts = mock(CloudAccountService.class);
    private final UUID userId = UUID.randomUUID();
    private final AccessTokenService tokens = mock(AccessTokenService.class);
    private final CloudSearchService.Options options = new CloudSearchService.Options("file", null, null, "all", "name");

    @Test
    void returnsIndependentSuccessAndFailure() {
        when(provider.type()).thenReturn(ProviderType.GOOGLE);
        var good = new AccountReference(UUID.randomUUID(), ProviderType.GOOGLE, "good");
        var bad = new AccountReference(UUID.randomUUID(), ProviderType.GOOGLE, "bad");
        var excluded = new AccountReference(UUID.randomUUID(), ProviderType.DROPBOX, "excluded");
        when(accounts.activeAccounts(userId)).thenReturn(List.of(good, bad, excluded));
        when(tokens.accessToken(good.id())).thenReturn("ok");
        when(tokens.accessToken(bad.id())).thenReturn("error");
        when(provider.search(eq("ok"), anyString(), any(), any(), anyString(), anyString(), anyString()))
            .thenReturn(new Page<>(List.of(), "next"));
        when(provider.search(eq("error"), anyString(), any(), any(), anyString(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("Provider diagnostics"));
        var search = new CloudSearchService(accounts, new ProviderRegistry(List.of(provider)), tokens, 1, 2, Duration.ofSeconds(2));
        try {
            var groups = search.searchAccounts(userId, " onedrive , google ", options);
            assertThat(groups).hasSize(2);
            assertThat(groups.get(0).nextPageToken()).isEqualTo("next");
            assertThat(groups.get(1).error()).isEqualTo("Provider search failed");
        } finally {
            search.stop();
        }
    }

    @Test
    void boundsQueueAndInterruptsSlowSearch() throws Exception {
        var interrupted = new CountDownLatch(1);
        var wait = new CountDownLatch(1);
        when(provider.type()).thenReturn(ProviderType.GOOGLE);
        when(tokens.accessToken(any())).thenReturn("slow");
        when(provider.search(eq("slow"), anyString(), any(), any(), anyString(), anyString(), anyString()))
            .thenAnswer(call -> {
                try {
                    wait.await();
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                    throw new IllegalStateException(exception);
                }
                return new Page<>(List.of(), null);
            });
        var selected = IntStream.range(0, 3)
            .mapToObj(i -> new AccountReference(UUID.randomUUID(), ProviderType.GOOGLE, Integer.toString(i))).toList();
        when(accounts.activeAccounts(userId)).thenReturn(selected);
        var search = new CloudSearchService(accounts, new ProviderRegistry(List.of(provider)), tokens, 1, 1, Duration.ofMillis(300));
        try {
            var groups = search.searchAccounts(userId, null, options);
            assertThat(groups).extracting(CloudSearchService.SearchGroup::error).containsExactly(
                "Provider search timed out", "Provider search timed out", "Search is busy. Try again.");
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            wait.countDown();
            search.stop();
        }
    }
}
