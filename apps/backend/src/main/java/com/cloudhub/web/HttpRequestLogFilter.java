package com.cloudhub.web;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpRequestLogFilter extends OncePerRequestFilter {
    static final String ERROR_CODE = HttpRequestLogFilter.class.getName() + ".errorCode";
    static final String ERROR_TYPE = HttpRequestLogFilter.class.getName() + ".errorType";
    private static final Logger LOG = LoggerFactory.getLogger("cloudhub.http");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        var completion = new Completion(request, response);
        response.setHeader("X-Request-ID", completion.id);
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException failure) {
            completion.failure = failure;
            throw failure;
        } finally {
            if (request.isAsyncStarted()) {
                request.getAsyncContext().addListener(completion);
            } else {
                completion.log();
            }
        }
    }

    private static final class Completion implements AsyncListener {
        private final String id = UUID.randomUUID().toString();
        private final long start = System.nanoTime();
        private final AtomicBoolean written = new AtomicBoolean();
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private volatile Throwable failure;

        Completion(HttpServletRequest request, HttpServletResponse response) {
            this.request = request;
            this.response = response;
        }

        void log() {
            if (!written.compareAndSet(false, true)) return;
            Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            Object code = request.getAttribute(ERROR_CODE);
            int status = response.getStatus();
            if (failure != null && status < 400 && !response.isCommitted()) status = 500;
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (status >= 500 || failure != null) {
                Object errorType = request.getAttribute(ERROR_TYPE);
                String diagnostic = failure == null ? (errorType == null ? "-" : errorType.toString())
                    : failure.getClass().getSimpleName();
                LOG.error("request_id={} method={} route={} status={} duration_ms={} code={} exception={}",
                    id, request.getMethod(), route == null ? "(unmatched)" : route, status, elapsed,
                    code == null ? "-" : code, diagnostic);
            } else if (status >= 400) {
                LOG.warn("request_id={} method={} route={} status={} duration_ms={} code={}",
                    id, request.getMethod(), route == null ? "(unmatched)" : route, status, elapsed,
                    code == null ? "-" : code);
            } else {
                LOG.info("request_id={} method={} route={} status={} duration_ms={}",
                    id, request.getMethod(), route == null ? "(unmatched)" : route, status, elapsed);
            }
        }

        @Override public void onComplete(AsyncEvent event) { log(); }
        @Override public void onError(AsyncEvent event) { failure = event.getThrowable(); }
        @Override public void onTimeout(AsyncEvent event) { failure = new IllegalStateException("Async timeout"); }
        @Override public void onStartAsync(AsyncEvent event) { event.getAsyncContext().addListener(this); }
    }
}
