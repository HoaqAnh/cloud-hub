package com.cloudhub.cloud;

import com.cloudhub.provider.FileItem;
import com.cloudhub.provider.GoogleApiException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.cloudhub.provider.Page;
import com.cloudhub.provider.ProviderRegistry;
import jakarta.annotation.PreDestroy;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CloudSearchService {
    private final ProviderRegistry providers;
    private final AccessTokenService tokens;
    private final Duration timeout;
    private final ThreadPoolExecutor workers;

    public CloudSearchService(ProviderRegistry providers, AccessTokenService tokens,
                              @Value("${cloudhub.search.workers:4}") int workerCount,
                              @Value("${cloudhub.search.queue-capacity:16}") int queueCapacity,
                              @Value("${cloudhub.search.timeout:10s}") Duration timeout) {
        this.providers = providers;
        this.tokens = tokens;
        this.timeout = timeout;
        this.workers = new ThreadPoolExecutor(workerCount, workerCount, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(queueCapacity), Thread.ofPlatform().name("cloud-search-", 0).factory());
    }

    public record Options(String q, String folderId, String pageToken, String type, String sort) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SearchGroup(String accountId, String accountName, String provider, String status,
                              List<FileItem> items, String nextPageToken, String error) {}

    private record Task(CloudAccount account, Future<SearchGroup> future) {}

    public Page<FileItem> searchAccount(CloudAccount account, Options options) {
        return providers.get(account.getProvider()).search(tokens.accessToken(account.getId()),
            options.q(), options.folderId(), options.pageToken(), account.getId().toString(),
            options.type(), options.sort());
    }

    public List<SearchGroup> searchAccounts(List<CloudAccount> accounts, Options options) {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<Task> tasks = accounts.stream().map(account -> submit(account, options)).toList();
        try {
            return tasks.stream().map(task -> await(task, deadline)).toList();
        } finally {
            for (Task task : tasks) {
                if (task.future() != null && !task.future().isDone()) task.future().cancel(true);
            }
            workers.purge();
        }
    }

    private Task submit(CloudAccount account, Options options) {
        try {
            return new Task(account, workers.submit(() -> {
                Page<FileItem> page = searchAccount(account, options);
                return group(account, "ok", page.items(), page.nextPageToken() == null ? "" : page.nextPageToken(), null);
            }));
        } catch (RejectedExecutionException exception) {
            return new Task(account, null);
        }
    }

    private SearchGroup await(Task task, long deadline) {
        if (task.future() == null) return error(task.account(), "Search is busy. Try again.");
        try {
            return task.future().get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            task.future().cancel(true);
            return error(task.account(), "Provider search timed out");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof GoogleApiException provider && provider.statusCode() == 401) {
                return error(task.account(), "Account needs reconnect");
            }
            for (; cause != null; cause = cause.getCause()) {
                if (cause instanceof HttpTimeoutException) return error(task.account(), "Provider search timed out");
            }
            return error(task.account(), "Provider search failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return error(task.account(), "Provider search interrupted");
        }
    }

    private SearchGroup error(CloudAccount account, String message) {
        return group(account, "error", List.of(), null, message);
    }

    private SearchGroup group(CloudAccount account, String status, List<FileItem> items, String nextPage, String error) {
        String provider = account.getProvider().name();
        String name = account.getDisplayName() == null ? provider : account.getDisplayName();
        return new SearchGroup(account.getId().toString(), name, provider, status, items, nextPage, error);
    }

    @PreDestroy
    void stop() {
        workers.shutdownNow();
    }
}
