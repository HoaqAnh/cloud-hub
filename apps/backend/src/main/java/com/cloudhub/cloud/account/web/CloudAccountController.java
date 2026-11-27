package com.cloudhub.cloud.account.web;

import com.cloudhub.cloud.account.application.CloudAccountService;
import com.cloudhub.cloud.account.application.CloudAccountService.AccountSummary;
import com.cloudhub.provider.StorageQuota;
import com.cloudhub.security.SessionIdentity;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class CloudAccountController {
    private final CloudAccountService accounts;
    private final SessionIdentity identity;

    public CloudAccountController(CloudAccountService accounts, SessionIdentity identity) {
        this.accounts = accounts;
        this.identity = identity;
    }

    @GetMapping
    public List<AccountSummary> listAccounts(HttpSession session) {
        return accounts.list(identity.requireUserId(session));
    }

    @GetMapping("/{accountId}/quota")
    public StorageQuota getQuota(@PathVariable UUID accountId, HttpSession session) {
        return accounts.quota(identity.requireUserId(session), accountId);
    }

    @DeleteMapping("/{accountId}")
    public Map<String, Boolean> disconnect(@PathVariable UUID accountId, HttpSession session) {
        accounts.disconnect(identity.requireUserId(session), accountId);
        return Map.of("disconnected", true);
    }
}
