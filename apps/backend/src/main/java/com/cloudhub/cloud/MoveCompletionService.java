package com.cloudhub.cloud;

import com.cloudhub.provider.GoogleApiException;
import com.cloudhub.provider.ProviderRegistry;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MoveCompletionService {
    private final PendingMoveRepository moves;
    private final CloudAccountRepository accounts;
    private final AccessTokenService tokens;
    private final ProviderRegistry providers;

    MoveCompletionService(PendingMoveRepository moves, CloudAccountRepository accounts, AccessTokenService tokens,
                          ProviderRegistry providers) {
        this.moves = moves;
        this.accounts = accounts;
        this.tokens = tokens;
        this.providers = providers;
    }

    UUID rememberFailure(UUID userId, UUID sourceAccountId, String sourceFileId) {
        return moves.save(new PendingMove(userId, sourceAccountId, sourceFileId)).id();
    }

    @Transactional
    public void retry(UUID id, UUID userId) {
        PendingMove move = moves.lockOwned(id, userId).orElseThrow(MoveCompletionService::missing);
        if (move.completed()) return;

        CloudAccount source = accounts.findById(move.sourceAccountId())
            .filter(account -> account.getUser().getId().equals(userId))
            .orElseThrow(MoveCompletionService::missing);
        String accessToken = tokens.accessToken(source.getId());
        try {
            providers.get(source.getProvider()).trash(accessToken, move.sourceFileId());
        } catch (GoogleApiException exception) {
            if (exception.statusCode() != 404) throw exception;
        }
        move.markCompleted();
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending move not found");
    }
}
