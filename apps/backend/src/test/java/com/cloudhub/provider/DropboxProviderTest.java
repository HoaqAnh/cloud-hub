package com.cloudhub.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;

import org.junit.jupiter.api.Test;

class DropboxProviderTest {
    private final DropboxProvider provider =
        new DropboxProvider("", "", "http://localhost:8080/api/oauth/dropbox/callback");

    @Test
    void exposesDropboxSprint4Capabilities() {
        Capabilities capabilities = provider.capabilities();

        assertTrue(capabilities.pagination());
        assertTrue(capabilities.search());
        assertTrue(capabilities.quota());
        assertTrue(capabilities.download());
        assertTrue(capabilities.upload());
        assertTrue(capabilities.createFolder());
        assertTrue(capabilities.rename());
        assertTrue(capabilities.trash());
        assertTrue(capabilities.thumbnail());
    }

    @Test
    void createsPkceChallengeForVerifier() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            provider.createCodeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        );
    }

    @Test
    void buildsDropboxV2ThumbnailRequestUsingFileId() {
        HttpRequest request = DropboxProvider.thumbnailRequest("token", "id:abc123");

        assertEquals("POST", request.method());
        assertEquals(
            "{\"resource\":{\".tag\":\"path\",\"path\":\"id:abc123\"},\"format\":\"jpeg\",\"size\":\"w128h128\",\"mode\":\"strict\"}",
            request.headers().firstValue("Dropbox-API-Arg").orElseThrow()
        );
    }

    @Test
    void onlyRequestsThumbnailsForDropboxSupportedImageExtensions() {
        assertTrue(DropboxProvider.supportsThumbnail("image.JPG"));
        assertTrue(DropboxProvider.supportsThumbnail("screenshot.png"));
        assertFalse(DropboxProvider.supportsThumbnail("document.pdf"));
        assertFalse(DropboxProvider.supportsThumbnail("drawing.svg"));
    }

    @Test
    void extractsFilenameWithExtensionFromDropboxDownloadMetadata() {
        assertEquals(
            "report.pdf",
            provider.filenameFromMetadataHeader("{\"name\":\"report.pdf\"}")
        );
    }
}
