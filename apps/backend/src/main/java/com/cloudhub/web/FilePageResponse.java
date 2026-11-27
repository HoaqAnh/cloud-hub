package com.cloudhub.web;

import com.cloudhub.provider.FileItem;
import com.cloudhub.provider.Page;
import java.util.List;

public record FilePageResponse(List<FileItem> items, String nextPageToken) {
    public static FilePageResponse from(Page<FileItem> page) {
        return new FilePageResponse(page.items(), page.nextPageToken() == null ? "" : page.nextPageToken());
    }
}
