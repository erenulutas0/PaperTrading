package com.finance.core.repository;

import com.finance.core.domain.StrategyBotRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface StrategyBotRunRepository extends JpaRepository<StrategyBotRun, UUID> {
    Page<StrategyBotRun> findByStrategyBotIdAndUserId(UUID strategyBotId, UUID userId, Pageable pageable);

    Optional<StrategyBotRun> findByIdAndStrategyBotIdAndUserId(UUID id, UUID strategyBotId, UUID userId);

    List<StrategyBotRun> findByStrategyBotIdAndUserIdOrderByRequestedAtDesc(UUID strategyBotId, UUID userId);

    List<StrategyBotRun> findByStrategyBotIdInOrderByRequestedAtDesc(Collection<UUID> strategyBotIds);

    List<StrategyBotRun> findByRunModeAndStatusOrderByRequestedAtAsc(StrategyBotRun.RunMode runMode, StrategyBotRun.Status status);

    void deleteByStrategyBotId(UUID strategyBotId);
}
