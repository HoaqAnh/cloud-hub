package com.cloudhub.cloud.transfer.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PendingMoveRepository extends JpaRepository<PendingMove, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select move from PendingMove move where move.id = :id and move.userId = :userId")
    Optional<PendingMove> lockOwned(UUID id, UUID userId);
}
