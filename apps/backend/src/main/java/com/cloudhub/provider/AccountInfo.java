package com.cloudhub.provider;

public record AccountInfo(
    String externalId,
    String displayName,
    String email,
    String scopes
) {}
