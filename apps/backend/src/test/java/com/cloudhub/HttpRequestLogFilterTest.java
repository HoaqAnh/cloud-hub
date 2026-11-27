package com.cloudhub;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class HttpRequestLogFilterTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger("cloudhub.http");
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final HttpRequestLogFilter filter = new HttpRequestLogFilter();

    @BeforeEach
    void capture() {
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(events);
        events.stop();
    }

    @Test
    void recordsFinalStatusAndApplicationErrorCodeWithoutSensitiveInputs() throws Exception {
        for (int status : new int[]{200, 400, 503}) {
            var request = new MockHttpServletRequest("GET", "/api/item/private-id");
            request.setQueryString("code=private-code");
            request.addHeader("Authorization", "Bearer private-token");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/item/{id}");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {
                request.setAttribute(HttpRequestLogFilter.ERROR_CODE, "TEST_CODE");
                response.setStatus(status);
            });
            assertThat(events.list.getLast().getFormattedMessage())
                .contains(response.getHeader("X-Request-ID"), "route=/api/item/{id}", "status=" + status, "duration_ms=")
                .doesNotContain("private");
            if (status >= 400) assertThat(events.list.getLast().getFormattedMessage()).contains("code=TEST_CODE");
        }
        assertThat(events.list).hasSize(3);
    }

    @Test
    void waitsUntilStreamingCompletes() throws ServletException, java.io.IOException {
        var request = new MockHttpServletRequest("GET", "/api/content/private-file");
        request.setAsyncSupported(true);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/content/{id}");
            req.startAsync(req, res);
        });
        assertThat(events.list).isEmpty();
        response.setStatus(206);
        request.getAsyncContext().complete();
        assertThat(events.list).singleElement().satisfies(event ->
            assertThat(event.getFormattedMessage()).contains(response.getHeader("X-Request-ID"), "status=206")
                .doesNotContain("private-file"));
    }
}
