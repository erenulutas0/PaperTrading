package com.finance.core.repository;

import com.finance.core.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedFalseOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("""
            update RefreshToken r
            set r.revoked = true,
                r.revokedAt = :revokedAt,
                r.lastUsedAt = :revokedAt
            where r.userId = :userId
              and r.revoked = false
            """)
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("revokedAt") LocalDateTime revokedAt);
}
