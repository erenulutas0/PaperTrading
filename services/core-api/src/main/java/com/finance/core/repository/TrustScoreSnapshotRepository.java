package com.finance.core.repository;

import com.finance.core.domain.TrustScoreSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrustScoreSnapshotRepository extends JpaRepository<TrustScoreSnapshot, UUID> {

    List<TrustScoreSnapshot> findByUserIdOrderByCapturedAtDesc(UUID userId, Pageable pageable);

    Optional<TrustScoreSnapshot> findTopByUserIdAndCapturedAtLessThanEqualOrderByCapturedAtDesc(UUID userId, LocalDateTime capturedAt);
}
