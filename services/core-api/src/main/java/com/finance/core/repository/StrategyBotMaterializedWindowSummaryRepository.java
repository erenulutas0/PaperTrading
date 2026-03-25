package com.finance.core.repository;

import com.finance.core.domain.StrategyBotMaterializedWindowSummary;
import com.finance.core.domain.StrategyBotMaterializedWindowSummaryId;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StrategyBotMaterializedWindowSummaryRepository extends JpaRepository<StrategyBotMaterializedWindowSummary, StrategyBotMaterializedWindowSummaryId> {

    @Query("""
            select s
            from StrategyBotMaterializedWindowSummary s
            where s.id.strategyBotId in :strategyBotIds
              and s.id.runModeScope = :runModeScope
              and s.id.lookbackDays = :lookbackDays
            """)
    List<StrategyBotMaterializedWindowSummary> findByStrategyBotIdInAndRunModeScopeAndLookbackDays(
            @Param("strategyBotIds") Collection<UUID> strategyBotIds,
            @Param("runModeScope") String runModeScope,
            @Param("lookbackDays") Integer lookbackDays);

    @Modifying
    @Transactional
    @Query("""
            delete from StrategyBotMaterializedWindowSummary s
            where s.id.strategyBotId = :strategyBotId
            """)
    void deleteByStrategyBotId(@Param("strategyBotId") UUID strategyBotId);
}
