package com.finance.core.repository;

import com.finance.core.domain.IdempotencyKeyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyRecord, UUID> {

    Optional<IdempotencyKeyRecord> findByActorScopeAndIdempotencyKey(String actorScope, String idempotencyKey);

    long countByStatus(IdempotencyKeyRecord.Status status);

    long countByExpiresAtBefore(LocalDateTime cutoff);

    Optional<IdempotencyKeyRecord> findFirstByExpiresAtBeforeOrderByExpiresAtAsc(LocalDateTime cutoff);

    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
