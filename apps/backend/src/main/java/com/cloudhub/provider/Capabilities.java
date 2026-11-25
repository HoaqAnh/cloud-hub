package com.cloudhub.provider;

public record Capabilities(
    boolean pagination,
    boolean search,
    boolean quota,
    boolean download,
    boolean upload,
    boolean createFolder,
    boolean rename,
    boolean trash,
    boolean thumbnail
) {
    public Capabilities(
        boolean pagination,
        boolean search,
        boolean quota,
        boolean download,
        boolean upload
    ) {
        this(pagination, search, quota, download, upload,
            true, true, true, true);
    }
}
