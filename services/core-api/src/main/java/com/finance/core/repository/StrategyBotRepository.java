package com.finance.core.repository;

import com.finance.core.domain.StrategyBot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StrategyBotRepository extends JpaRepository<StrategyBot, UUID> {
    Page<StrategyBot> findByUserId(UUID userId, Pageable pageable);

    Optional<StrategyBot> findByIdAndUserId(UUID id, UUID userId);
}
