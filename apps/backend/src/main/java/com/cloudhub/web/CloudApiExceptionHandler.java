package com.cloudhub.web;

import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;

import com.cloudhub.provider.GoogleApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CloudApiExceptionHandler {

    @ExceptionHandler(GoogleApiException.class)
    public ProblemDetail googleApi(GoogleApiException exception, HttpServletRequest request) {
        HttpStatus status = exception.statusCode() == 401
            ? HttpStatus.UNAUTHORIZED
            : exception.statusCode() == 403
                ? HttpStatus.FORBIDDEN
                : exception.statusCode() == 404
                    ? HttpStatus.NOT_FOUND
                    : exception.statusCode() == 429
                        ? HttpStatus.TOO_MANY_REQUESTS
                        : HttpStatus.BAD_GATEWAY;
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, switch (status) {
            case UNAUTHORIZED -> "Account needs reconnect";
            case FORBIDDEN -> "Cloud provider denied the request";
            case NOT_FOUND -> "Cloud item not found";
            case TOO_MANY_REQUESTS -> "Cloud provider rate limit exceeded";
            default -> "Cloud provider request failed";
        });
        detail.setType(URI.create("about:blank"));
        detail.setProperty("code", switch (status) {
            case UNAUTHORIZED -> "PROVIDER_UNAUTHORIZED";
            case FORBIDDEN -> "PROVIDER_FORBIDDEN";
            case NOT_FOUND -> "PROVIDER_NOT_FOUND";
            case TOO_MANY_REQUESTS -> "PROVIDER_RATE_LIMITED";
            default -> "PROVIDER_UNAVAILABLE";
        });
        request.setAttribute(HttpRequestLogFilter.ERROR_CODE, detail.getProperties().get("code"));
        request.setAttribute(HttpRequestLogFilter.ERROR_TYPE, exception.getClass().getSimpleName());
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidRequest(IllegalArgumentException exception, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setProperty("code", "INVALID_REQUEST");
        request.setAttribute(HttpRequestLogFilter.ERROR_CODE, detail.getProperties().get("code"));
        request.setAttribute(HttpRequestLogFilter.ERROR_TYPE, exception.getClass().getSimpleName());
        return detail;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail unavailable(IllegalStateException exception, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            exception.getMessage()
        );
        detail.setProperty("code", "PROVIDER_UNAVAILABLE");
        request.setAttribute(HttpRequestLogFilter.ERROR_CODE, detail.getProperties().get("code"));
        request.setAttribute(HttpRequestLogFilter.ERROR_TYPE, exception.getClass().getSimpleName());
        return detail;
    }
}
