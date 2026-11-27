package com.cloudhub.cloud;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "pending_moves")
class PendingMove {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "source_file_id", nullable = false)
    private String sourceFileId;

    @Column(nullable = false)
    private boolean completed;

    protected PendingMove() {}

    PendingMove(UUID userId, UUID sourceAccountId, String sourceFileId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.sourceAccountId = sourceAccountId;
        this.sourceFileId = sourceFileId;
    }

    UUID id() { return id; }
    UUID sourceAccountId() { return sourceAccountId; }
    String sourceFileId() { return sourceFileId; }
    boolean completed() { return completed; }
    void markCompleted() { completed = true; }
}
