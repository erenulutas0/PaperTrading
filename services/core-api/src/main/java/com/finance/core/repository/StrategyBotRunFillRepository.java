package com.finance.core.repository;

import com.finance.core.domain.StrategyBotRunFill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StrategyBotRunFillRepository extends JpaRepository<StrategyBotRunFill, UUID> {
    Page<StrategyBotRunFill> findByStrategyBotRunIdOrderBySequenceNoAsc(UUID strategyBotRunId, Pageable pageable);

    void deleteByStrategyBotRunId(UUID strategyBotRunId);
}
