package com.cloudhub.cloud.transfer.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "pending_moves")
public class PendingMove {
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

    public PendingMove(UUID userId, UUID sourceAccountId, String sourceFileId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.sourceAccountId = sourceAccountId;
        this.sourceFileId = sourceFileId;
    }

    public UUID id() { return id; }
    public UUID sourceAccountId() { return sourceAccountId; }
    public String sourceFileId() { return sourceFileId; }
    public boolean completed() { return completed; }
    public void markCompleted() { completed = true; }
}
