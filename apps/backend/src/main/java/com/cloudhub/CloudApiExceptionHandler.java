package com.cloudhub;

import java.net.URI;

import com.cloudhub.provider.GoogleApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CloudApiExceptionHandler {

    @ExceptionHandler(GoogleApiException.class)
    public ProblemDetail googleApi(GoogleApiException exception) {
        HttpStatus status = exception.statusCode() == 401
            ? HttpStatus.UNAUTHORIZED
            : exception.statusCode() == 403
                ? HttpStatus.FORBIDDEN
                : exception.statusCode() == 404
                    ? HttpStatus.NOT_FOUND
                    : exception.statusCode() == 429
                        ? HttpStatus.TOO_MANY_REQUESTS
                        : HttpStatus.BAD_GATEWAY;
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setType(URI.create("about:blank"));
        detail.setProperty("code", switch (status) {
            case UNAUTHORIZED -> "PROVIDER_UNAUTHORIZED";
            case FORBIDDEN -> "PROVIDER_FORBIDDEN";
            case NOT_FOUND -> "PROVIDER_NOT_FOUND";
            case TOO_MANY_REQUESTS -> "PROVIDER_RATE_LIMITED";
            default -> "PROVIDER_UNAVAILABLE";
        });
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidRequest(IllegalArgumentException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setProperty("code", "INVALID_REQUEST");
        return detail;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail unavailable(IllegalStateException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            exception.getMessage()
        );
        detail.setProperty("code", "PROVIDER_UNAVAILABLE");
        return detail;
    }
}
