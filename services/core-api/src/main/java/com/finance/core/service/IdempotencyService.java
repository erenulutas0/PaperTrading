package com.finance.core.service;

import com.finance.core.domain.IdempotencyKeyRecord;
import com.finance.core.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Transactional
    public ClaimResult claim(
            String actorScope,
            String idempotencyKey,
            String requestMethod,
            String requestPath,
            String requestHash,
            LocalDateTime expiresAt) {

        IdempotencyKeyRecord existing = idempotencyKeyRepository
                .findByActorScopeAndIdempotencyKey(actorScope, idempotencyKey)
                .orElse(null);

        if (existing != null) {
            return evaluateExisting(existing, requestMethod, requestPath, requestHash, expiresAt);
        }

        IdempotencyKeyRecord created = IdempotencyKeyRecord.builder()
                .actorScope(actorScope)
                .idempotencyKey(idempotencyKey)
                .requestMethod(requestMethod)
                .requestPath(requestPath)
                .requestHash(requestHash)
                .status(IdempotencyKeyRecord.Status.IN_PROGRESS)
                .expiresAt(expiresAt)
                .build();
        try {
            return ClaimResult.claimed(idempotencyKeyRepository.save(created));
        } catch (DataIntegrityViolationException e) {
            IdempotencyKeyRecord raced = idempotencyKeyRepository
                    .findByActorScopeAndIdempotencyKey(actorScope, idempotencyKey)
                    .orElseThrow();
            return evaluateExisting(raced, requestMethod, requestPath, requestHash, expiresAt);
        }
    }

    @Transactional
    public void complete(
            IdempotencyKeyRecord record,
            int responseStatus,
            String responseContentType,
            String responseBody) {

        record.setStatus(IdempotencyKeyRecord.Status.COMPLETED);
        record.setResponseStatus(responseStatus);
        record.setResponseContentType(responseContentType);
        record.setResponseBody(responseBody);
        record.setCompletedAt(LocalDateTime.now());
        idempotencyKeyRepository.save(record);
    }

    @Transactional
    public void release(IdempotencyKeyRecord record) {
        idempotencyKeyRepository.delete(record);
    }

    @Transactional
    public void purgeExpired(LocalDateTime cutoff) {
        idempotencyKeyRepository.deleteByExpiresAtBefore(cutoff);
    }

    private ClaimResult evaluateExisting(
            IdempotencyKeyRecord existing,
            String requestMethod,
            String requestPath,
            String requestHash,
            LocalDateTime expiresAt) {

        if (existing.getExpiresAt() != null && existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            idempotencyKeyRepository.delete(existing);
            IdempotencyKeyRecord recreated = IdempotencyKeyRecord.builder()
                    .actorScope(existing.getActorScope())
                    .idempotencyKey(existing.getIdempotencyKey())
                    .requestMethod(requestMethod)
                    .requestPath(requestPath)
                    .requestHash(requestHash)
                    .status(IdempotencyKeyRecord.Status.IN_PROGRESS)
                    .expiresAt(expiresAt)
                    .build();
            return ClaimResult.claimed(idempotencyKeyRepository.save(recreated));
        }

        boolean mismatched = !existing.getRequestMethod().equals(requestMethod)
                || !existing.getRequestPath().equals(requestPath)
                || !existing.getRequestHash().equals(requestHash);
        if (mismatched) {
            return ClaimResult.conflict(existing);
        }

        if (existing.getStatus() == IdempotencyKeyRecord.Status.COMPLETED) {
            return ClaimResult.replay(existing);
        }

        return ClaimResult.inProgress(existing);
    }

    public record ClaimResult(Type type, IdempotencyKeyRecord record) {

        public enum Type {
            CLAIMED,
            REPLAY,
            CONFLICT,
            IN_PROGRESS
        }

        public static ClaimResult claimed(IdempotencyKeyRecord record) {
            return new ClaimResult(Type.CLAIMED, record);
        }

        public static ClaimResult replay(IdempotencyKeyRecord record) {
            return new ClaimResult(Type.REPLAY, record);
        }

        public static ClaimResult conflict(IdempotencyKeyRecord record) {
            return new ClaimResult(Type.CONFLICT, record);
        }

        public static ClaimResult inProgress(IdempotencyKeyRecord record) {
            return new ClaimResult(Type.IN_PROGRESS, record);
        }
    }
}
