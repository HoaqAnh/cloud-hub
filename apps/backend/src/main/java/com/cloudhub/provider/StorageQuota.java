package com.cloudhub.provider;

public record StorageQuota(long limit, long usage, long usageInDrive, long usageInTrash) {}
