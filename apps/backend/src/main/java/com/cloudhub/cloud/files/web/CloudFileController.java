package com.cloudhub.cloud.files.web;

import com.cloudhub.cloud.files.application.CloudFileService;
import com.cloudhub.provider.DownloadStream;
import com.cloudhub.provider.FileItem;
import com.cloudhub.security.SessionIdentity;
import com.cloudhub.web.FilePageResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/accounts/{accountId}")
public class CloudFileController {
    private final CloudFileService files;
    private final SessionIdentity identity;

    public CloudFileController(CloudFileService files, SessionIdentity identity) {
        this.files = files;
        this.identity = identity;
    }

    @GetMapping("/files")
    public FilePageResponse listFiles(
        @PathVariable UUID accountId,
        @RequestParam(required = false) String folderId,
        @RequestParam(required = false) String pageToken,
        @RequestParam(defaultValue = "all") String type,
        @RequestParam(defaultValue = "name") String sort,
        HttpSession session
    ) {
        return FilePageResponse.from(files.list(identity.requireUserId(session), accountId, folderId, pageToken, type, sort));
    }

    @GetMapping("/files/{fileId}")
    public FileItem getFile(@PathVariable UUID accountId, @PathVariable String fileId, HttpSession session) {
        return files.metadata(identity.requireUserId(session), accountId, fileId);
    }

    @GetMapping("/files/{fileId}/content")
    public ResponseEntity<StreamingResponseBody> downloadFile(
        @PathVariable UUID accountId,
        @PathVariable String fileId,
        @RequestParam(defaultValue = "attachment") String disposition,
        HttpSession session
    ) {
        DownloadStream download = files.download(identity.requireUserId(session), accountId, fileId);
        String safeFilename = URLEncoder.encode(download.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        String contentDisposition = "inline".equals(disposition) ? "inline" : "attachment";
        HttpHeaders headers = streamHeaders(download);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition + "; filename*=UTF-8''" + safeFilename);
        return stream(download, headers);
    }

    @PostMapping("/files")
    public FileItem uploadFile(
        @PathVariable UUID accountId,
        @RequestParam(required = false) String parentId,
        @RequestPart("file") MultipartFile file,
        HttpSession session
    ) throws IOException {
        UUID userId = identity.requireUserId(session);
        String mimeType = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        try (var input = file.getInputStream()) {
            return files.upload(userId, accountId, parentId, file.getOriginalFilename(), mimeType, input, file.getSize());
        }
    }

    @GetMapping("/files/{fileId}/thumbnail")
    public ResponseEntity<StreamingResponseBody> thumbnail(
        @PathVariable UUID accountId, @PathVariable String fileId, HttpSession session
    ) {
        DownloadStream download = files.thumbnail(identity.requireUserId(session), accountId, fileId);
        HttpHeaders headers = streamHeaders(download);
        headers.setCacheControl("private, max-age=300");
        return stream(download, headers);
    }

    @PostMapping("/folders")
    public FileItem createFolder(
        @PathVariable UUID accountId,
        @RequestParam(required = false) String parentId,
        @RequestParam String name,
        HttpSession session
    ) {
        return files.createFolder(identity.requireUserId(session), accountId, parentId, name);
    }

    @PatchMapping("/files/{fileId}")
    public FileItem renameFile(
        @PathVariable UUID accountId, @PathVariable String fileId, @RequestParam String name, HttpSession session
    ) {
        return files.rename(identity.requireUserId(session), accountId, fileId, name);
    }

    @DeleteMapping("/files/{fileId}")
    public Map<String, Boolean> trashFile(@PathVariable UUID accountId, @PathVariable String fileId, HttpSession session) {
        files.trash(identity.requireUserId(session), accountId, fileId);
        return Map.of("trashed", true);
    }

    private static HttpHeaders streamHeaders(DownloadStream download) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.mimeType()));
        headers.set("X-Content-Type-Options", "nosniff");
        if (download.size() >= 0) headers.setContentLength(download.size());
        return headers;
    }

    private static ResponseEntity<StreamingResponseBody> stream(DownloadStream download, HttpHeaders headers) {
        return ResponseEntity.ok().headers(headers).body(output -> {
            try (var input = download.input()) {
                input.transferTo(output);
            }
        });
    }
}
