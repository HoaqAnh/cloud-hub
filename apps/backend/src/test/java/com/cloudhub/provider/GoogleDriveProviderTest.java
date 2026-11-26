package com.cloudhub.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpRequest;

import org.junit.jupiter.api.Test;

class GoogleDriveProviderTest {

    private final GoogleDriveProvider provider =
        new GoogleDriveProvider("", "", "http://localhost:8080/api/oauth/google/callback");

    @Test
    void exposesSprint2DriveCapabilities() {
        Capabilities capabilities = provider.capabilities();

        assertTrue(capabilities.pagination());
        assertTrue(capabilities.download());
        assertTrue(capabilities.upload());
    }

    @Test
    void exposesSprint3DriveCapabilities() {
        Capabilities capabilities = provider.capabilities();

        assertTrue(capabilities.search());
        assertTrue(capabilities.quota());
    }

    @Test
    void createsPkceChallengeForVerifier() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            provider.createCodeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        );
    }

    @Test
    void createsUploadRequestWithKnownContentLength() {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.com/upload"))
            .PUT(GoogleDriveProvider.uploadBody(
                new ByteArrayInputStream(new byte[] {1, 2, 3}),
                3
            ))
            .build();

        assertEquals(3, request.bodyPublisher().orElseThrow().contentLength());
    }

    @Test
    void createsEmptyUploadRequestBody() {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.com/upload"))
            .PUT(GoogleDriveProvider.uploadBody(new ByteArrayInputStream(new byte[0]), 0))
            .build();

        assertEquals(0, request.bodyPublisher().orElseThrow().contentLength());
    }

    @Test
    void rejectsNegativeUploadSize() {
        assertThrows(
            IllegalArgumentException.class,
            () -> GoogleDriveProvider.uploadBody(new ByteArrayInputStream(new byte[0]), -1)
        );
    }

    @Test
    void encodesDriveOrderByValuesForDescendingSorts() {
        String[] sorts = {"name-desc", "size-desc", "modified-desc"};
        String[] expectedQueries = {
            "orderBy=folder%2Cname+desc",
            "orderBy=folder%2CquotaBytesUsed+desc",
            "orderBy=folder%2CmodifiedTime+desc"
        };
        for (int i = 0; i < sorts.length; i++) {
            URI uri = URI.create(
                "https://www.googleapis.com/drive/v3/files?orderBy="
                    + GoogleDriveProvider.encodeOrder(sorts[i])
            );

            assertEquals(expectedQueries[i], uri.getRawQuery());
        }
    }
}
