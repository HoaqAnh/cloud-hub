package com.cloudhub.provider;

import java.time.Instant;

public record TokenSet(String accessToken, String refreshToken, Instant expiresAt) {}
