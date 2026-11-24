package com.cloudhub.provider;

public class GoogleApiException extends RuntimeException {
    private final int statusCode;

    public GoogleApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
