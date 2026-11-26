package com.cloudhub.provider;

import java.time.Instant;

public record FileItem(
    String id,
    String accountId,
    ProviderType provider,
    String name,
    String type,
    String mimeType,
    Long size,
    Instant modifiedAt,
    String parentId,
    boolean shortcut,
    String shortcutTargetId,
    boolean nativeDoc,
    String thumbnailUrl,
    boolean previewable
) {}
