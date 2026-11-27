package com.cloudhub.provider.onedrive;

import com.cloudhub.provider.Capabilities;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OneDriveProviderTest {
    private final OneDriveProvider provider =
        new OneDriveProvider("", "", "http://localhost:8080/api/oauth/onedrive/callback", "common");

    @Test
    void exposesSprint5OneDriveCapabilities() {
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
}
