package com.cloudhub.cloud.oauth.web;

import com.cloudhub.cloud.oauth.application.OAuthService;
import com.cloudhub.provider.ProviderType;
import com.cloudhub.security.SessionIdentity;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oauth")
public class OAuthController {
    private final OAuthService oauth;

    public OAuthController(OAuthService oauth) {
        this.oauth = oauth;
    }

    @GetMapping("/{provider:google|dropbox|onedrive}/start")
    public void start(@PathVariable String provider, HttpSession session, HttpServletResponse response) throws IOException {
        response.sendRedirect(oauth.start(SessionIdentity.userId(session), providerType(provider)).toString());
    }

    @GetMapping("/{provider:google|dropbox|onedrive}/callback")
    public void callback(@PathVariable String provider, @RequestParam String code, @RequestParam String state,
                         HttpSession session, HttpServletResponse response) throws IOException {
        oauth.complete(SessionIdentity.userId(session), providerType(provider), code, state);
        response.sendRedirect("http://localhost:5173/dashboard");
    }

    private static ProviderType providerType(String provider) {
        return ProviderType.valueOf(provider.toUpperCase(Locale.ROOT));
    }
}
