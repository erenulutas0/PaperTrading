package com.finance.core.repository;

import com.finance.core.domain.MarketTerminalLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketTerminalLayoutRepository extends JpaRepository<MarketTerminalLayout, UUID> {
    List<MarketTerminalLayout> findByUserIdOrderByUpdatedAtDescCreatedAtDesc(UUID userId);

    Optional<MarketTerminalLayout> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
