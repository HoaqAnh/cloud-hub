package com.cloudhub.cloud.transfer.application;

import com.cloudhub.cloud.account.application.AccessTokenService;
import com.cloudhub.cloud.account.application.CloudAccountService;
import com.cloudhub.cloud.transfer.persistence.PendingMove;
import com.cloudhub.cloud.transfer.persistence.PendingMoveRepository;
import com.cloudhub.provider.GoogleApiException;
import com.cloudhub.provider.ProviderRegistry;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MoveCompletionService {
    private final PendingMoveRepository moves;
    private final CloudAccountService accounts;
    private final AccessTokenService tokens;
    private final ProviderRegistry providers;

    MoveCompletionService(PendingMoveRepository moves, CloudAccountService accounts, AccessTokenService tokens,
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
    public boolean retry(UUID id, UUID userId) {
        PendingMove move = moves.lockOwned(id, userId).orElse(null);
        if (move == null) return false;
        if (move.completed()) return true;

        var source = accounts.findOwned(userId, move.sourceAccountId()).orElse(null);
        if (source == null) return false;
        String accessToken = tokens.accessToken(source.id());
        try {
            providers.get(source.provider()).trash(accessToken, move.sourceFileId());
        } catch (GoogleApiException exception) {
            if (exception.statusCode() != 404) throw exception;
        }
        move.markCompleted();
        return true;
    }
}
