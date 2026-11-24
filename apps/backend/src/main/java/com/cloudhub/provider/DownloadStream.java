package com.cloudhub.provider;

import java.io.InputStream;

public record DownloadStream(
    InputStream input,
    long size,
    String mimeType,
    String filename
) {}
