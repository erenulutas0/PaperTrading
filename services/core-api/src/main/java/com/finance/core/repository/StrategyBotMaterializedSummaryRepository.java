package com.finance.core.repository;

import com.finance.core.domain.StrategyBotMaterializedSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StrategyBotMaterializedSummaryRepository extends JpaRepository<StrategyBotMaterializedSummary, UUID> {

    List<StrategyBotMaterializedSummary> findByStrategyBotIdIn(Collection<UUID> strategyBotIds);
}
