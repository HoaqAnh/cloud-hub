package com.cloudhub.cloud.transfer.web;

import com.cloudhub.cloud.transfer.application.TransferService;
import com.cloudhub.cloud.transfer.application.TransferService.TransferCommand;
import com.cloudhub.cloud.transfer.application.TransferService.TransferResult;
import com.cloudhub.security.SessionIdentity;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    private final TransferService transfers;
    private final SessionIdentity identity;

    public TransferController(TransferService transfers, SessionIdentity identity) {
        this.transfers = transfers;
        this.identity = identity;
    }

    @PostMapping
    public TransferResult transferFile(@RequestBody TransferRequest request, HttpSession session) throws IOException {
        return transfers.transfer(identity.requireUserId(session), new TransferCommand(request.sourceAccountId(),
            request.sourceFileId(), request.destinationAccountId(), request.destinationFolderId(), request.mode()));
    }

    @PostMapping("/{retryId}/retry-source")
    public Map<String, Boolean> retryMove(@PathVariable UUID retryId, HttpSession session) {
        if (!transfers.retryMove(identity.requireUserId(session), retryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending move not found");
        }
        return Map.of("moved", true);
    }

    public record TransferRequest(UUID sourceAccountId, String sourceFileId, UUID destinationAccountId,
                                  String destinationFolderId, String mode) {}
}
