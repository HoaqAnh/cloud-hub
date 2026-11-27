package com.cloudhub.cloud.search.web;

import com.cloudhub.cloud.search.application.CloudSearchService;
import com.cloudhub.cloud.search.application.CloudSearchService.Options;
import com.cloudhub.cloud.search.application.CloudSearchService.SearchGroup;
import com.cloudhub.provider.FileItem;
import com.cloudhub.security.SessionIdentity;
import com.cloudhub.web.FilePageResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CloudSearchController {
    private final CloudSearchService search;
    private final SessionIdentity identity;

    public CloudSearchController(CloudSearchService search, SessionIdentity identity) {
        this.search = search;
        this.identity = identity;
    }

    @GetMapping("/api/search")
    public Object searchFiles(
        @RequestParam String q,
        @RequestParam(required = false) String accountId,
        @RequestParam(required = false) String providers,
        @RequestParam(required = false) String folderId,
        @RequestParam(required = false) String pageToken,
        @RequestParam(defaultValue = "all") String type,
        @RequestParam(defaultValue = "name") String sort,
        HttpSession session
    ) {
        UUID userId = identity.requireUserId(session);
        var options = new Options(q, folderId, pageToken, type, sort);
        if (accountId != null && !accountId.isBlank()) {
            return FilePageResponse.from(search.searchAccount(userId, UUID.fromString(accountId), options));
        }
        return new GroupedSearchResponse(q, search.searchAccounts(userId, providers, options).stream()
            .map(SearchGroupResponse::from).toList());
    }

    public record GroupedSearchResponse(String q, List<SearchGroupResponse> groups) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SearchGroupResponse(String accountId, String accountName, String provider, String status,
                                      List<FileItem> items, String nextPageToken, String error) {
        private static SearchGroupResponse from(SearchGroup group) {
            return new SearchGroupResponse(group.accountId(), group.accountName(), group.provider(), group.status(),
                group.items(), group.nextPageToken(), group.error());
        }
    }
}
