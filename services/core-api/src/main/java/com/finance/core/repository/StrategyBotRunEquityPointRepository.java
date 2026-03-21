package com.finance.core.repository;

import com.finance.core.domain.StrategyBotRunEquityPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StrategyBotRunEquityPointRepository extends JpaRepository<StrategyBotRunEquityPoint, UUID> {
    Page<StrategyBotRunEquityPoint> findByStrategyBotRunIdOrderBySequenceNoAsc(UUID strategyBotRunId, Pageable pageable);

    Optional<StrategyBotRunEquityPoint> findFirstByStrategyBotRunIdOrderBySequenceNoDesc(UUID strategyBotRunId);

    void deleteByStrategyBotRunId(UUID strategyBotRunId);
}
