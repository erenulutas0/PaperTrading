package com.finance.core.repository;

import com.finance.core.domain.StrategyBotRunEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StrategyBotRunEventRepository extends JpaRepository<StrategyBotRunEvent, UUID> {

    Page<StrategyBotRunEvent> findByStrategyBotRunIdOrderBySequenceNoAsc(UUID strategyBotRunId, Pageable pageable);

    void deleteByStrategyBotRunId(UUID strategyBotRunId);
}
