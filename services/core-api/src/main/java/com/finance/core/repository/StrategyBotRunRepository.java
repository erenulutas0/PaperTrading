package com.finance.core.repository;

import com.finance.core.domain.StrategyBotRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface StrategyBotRunRepository extends JpaRepository<StrategyBotRun, UUID> {
    Page<StrategyBotRun> findByStrategyBotIdAndUserId(UUID strategyBotId, UUID userId, Pageable pageable);

    Optional<StrategyBotRun> findByIdAndStrategyBotIdAndUserId(UUID id, UUID strategyBotId, UUID userId);

    List<StrategyBotRun> findByStrategyBotIdAndUserIdOrderByRequestedAtDesc(UUID strategyBotId, UUID userId);

    List<StrategyBotRun> findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(Collection<UUID> strategyBotIds, UUID userId);

    List<StrategyBotRun> findByStrategyBotIdOrderByRequestedAtDesc(UUID strategyBotId);

    List<StrategyBotRun> findByStrategyBotIdInOrderByRequestedAtDesc(Collection<UUID> strategyBotIds);

    List<StrategyBotRun> findByIdIn(Collection<UUID> ids);

    List<StrategyBotRun> findByRunModeAndStatusOrderByRequestedAtAsc(StrategyBotRun.RunMode runMode, StrategyBotRun.Status status);

    void deleteByStrategyBotId(UUID strategyBotId);

    @Query(value = """
            SELECT
                r.strategy_bot_id AS strategyBotId,
                COUNT(*) AS totalRuns,
                SUM(CASE WHEN r.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedRuns,
                SUM(CASE WHEN r.status = 'RUNNING' THEN 1 ELSE 0 END) AS runningRuns,
                SUM(CASE WHEN r.status = 'FAILED' THEN 1 ELSE 0 END) AS failedRuns,
                SUM(CASE WHEN r.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelledRuns,
                SUM(CASE WHEN r.run_mode = 'BACKTEST' THEN 1 ELSE 0 END) AS backtestRuns,
                SUM(CASE WHEN r.run_mode = 'FORWARD_TEST' THEN 1 ELSE 0 END) AS forwardTestRuns,
                SUM(CASE WHEN COALESCE((r.summary::jsonb ->> 'executionEngineReady')::boolean, false) = true THEN 1 ELSE 0 END) AS compilerReadyRuns,
                SUM(CASE WHEN r.status = 'COMPLETED'
                         AND COALESCE(NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision, 0) > 0
                         THEN 1 ELSE 0 END) AS positiveCompletedRuns,
                SUM(CASE WHEN r.status = 'COMPLETED'
                         AND COALESCE(NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision, 0) < 0
                         THEN 1 ELSE 0 END) AS negativeCompletedRuns,
                COALESCE(SUM(CASE WHEN r.status = 'COMPLETED'
                                  THEN COALESCE(NULLIF(r.summary::jsonb ->> 'tradeCount', '')::integer, 0)
                                  ELSE 0 END), 0) AS totalSimulatedTrades,
                AVG(CASE WHEN r.status = 'COMPLETED'
                         THEN NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision
                         END) AS avgReturnPercent,
                AVG(CASE WHEN r.status = 'COMPLETED'
                         THEN NULLIF(r.summary::jsonb ->> 'netPnl', '')::double precision
                         END) AS avgNetPnl,
                AVG(CASE WHEN r.status = 'COMPLETED'
                         THEN NULLIF(r.summary::jsonb ->> 'maxDrawdownPercent', '')::double precision
                         END) AS avgMaxDrawdownPercent,
                AVG(CASE WHEN r.status = 'COMPLETED'
                         THEN NULLIF(r.summary::jsonb ->> 'winRate', '')::double precision
                         END) AS avgWinRate,
                AVG(CASE WHEN r.status = 'COMPLETED'
                         THEN NULLIF(r.summary::jsonb ->> 'tradeCount', '')::double precision
                         END) AS avgTradeCount,
                AVG(CASE WHEN r.status = 'COMPLETED'
                         THEN NULLIF(r.summary::jsonb ->> 'profitFactor', '')::double precision
                         END) AS avgProfitFactor,
                AVG(CASE WHEN r.status = 'COMPLETED'
                         THEN NULLIF(r.summary::jsonb ->> 'expectancyPerTrade', '')::double precision
                         END) AS avgExpectancyPerTrade,
                MAX(r.requested_at) AS latestRequestedAt
            FROM strategy_bot_runs r
            WHERE r.strategy_bot_id IN (:strategyBotIds)
              AND (:runModeScope = 'ALL' OR r.run_mode = CAST(:runModeScope AS varchar))
              AND (:lookbackActive = false OR COALESCE(r.completed_at, r.requested_at) >= :lookbackCutoff)
            GROUP BY r.strategy_bot_id
            """, nativeQuery = true)
    List<BoardAggregateView> findBoardAggregatesByStrategyBotIdIn(
            @Param("strategyBotIds") Collection<UUID> strategyBotIds,
            @Param("runModeScope") String runModeScope,
            @Param("lookbackActive") boolean lookbackActive,
            @Param("lookbackCutoff") LocalDateTime lookbackCutoff);

    @Query(value = """
            SELECT picked.strategy_bot_id AS strategyBotId, picked.id AS id
            FROM (
                SELECT r.strategy_bot_id,
                       r.id,
                       ROW_NUMBER() OVER (
                           PARTITION BY r.strategy_bot_id
                           ORDER BY NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision DESC NULLS LAST,
                                    COALESCE(r.completed_at, r.requested_at) DESC,
                                    r.id ASC
                       ) AS rn
                FROM strategy_bot_runs r
                WHERE r.strategy_bot_id IN (:strategyBotIds)
                  AND r.status = 'COMPLETED'
                  AND (:runModeScope = 'ALL' OR r.run_mode = CAST(:runModeScope AS varchar))
                  AND (:lookbackActive = false OR COALESCE(r.completed_at, r.requested_at) >= :lookbackCutoff)
            ) picked
            WHERE picked.rn = 1
            """, nativeQuery = true)
    List<SelectedRunView> findBestCompletedRunIdsByStrategyBotIdIn(
            @Param("strategyBotIds") Collection<UUID> strategyBotIds,
            @Param("runModeScope") String runModeScope,
            @Param("lookbackActive") boolean lookbackActive,
            @Param("lookbackCutoff") LocalDateTime lookbackCutoff);

    @Query(value = """
            SELECT picked.strategy_bot_id AS strategyBotId, picked.id AS id
            FROM (
                SELECT r.strategy_bot_id,
                       r.id,
                       ROW_NUMBER() OVER (
                           PARTITION BY r.strategy_bot_id
                           ORDER BY NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision ASC NULLS LAST,
                                    COALESCE(r.completed_at, r.requested_at) DESC,
                                    r.id ASC
                       ) AS rn
                FROM strategy_bot_runs r
                WHERE r.strategy_bot_id IN (:strategyBotIds)
                  AND r.status = 'COMPLETED'
                  AND (:runModeScope = 'ALL' OR r.run_mode = CAST(:runModeScope AS varchar))
                  AND (:lookbackActive = false OR COALESCE(r.completed_at, r.requested_at) >= :lookbackCutoff)
            ) picked
            WHERE picked.rn = 1
            """, nativeQuery = true)
    List<SelectedRunView> findWorstCompletedRunIdsByStrategyBotIdIn(
            @Param("strategyBotIds") Collection<UUID> strategyBotIds,
            @Param("runModeScope") String runModeScope,
            @Param("lookbackActive") boolean lookbackActive,
            @Param("lookbackCutoff") LocalDateTime lookbackCutoff);

    @Query(value = """
            SELECT picked.strategy_bot_id AS strategyBotId, picked.id AS id
            FROM (
                SELECT r.strategy_bot_id,
                       r.id,
                       ROW_NUMBER() OVER (
                           PARTITION BY r.strategy_bot_id
                           ORDER BY r.completed_at DESC NULLS LAST,
                                    r.requested_at DESC,
                                    r.id ASC
                       ) AS rn
                FROM strategy_bot_runs r
                WHERE r.strategy_bot_id IN (:strategyBotIds)
                  AND r.status = 'COMPLETED'
                  AND (:runModeScope = 'ALL' OR r.run_mode = CAST(:runModeScope AS varchar))
                  AND (:lookbackActive = false OR COALESCE(r.completed_at, r.requested_at) >= :lookbackCutoff)
            ) picked
            WHERE picked.rn = 1
            """, nativeQuery = true)
    List<SelectedRunView> findLatestCompletedRunIdsByStrategyBotIdIn(
            @Param("strategyBotIds") Collection<UUID> strategyBotIds,
            @Param("runModeScope") String runModeScope,
            @Param("lookbackActive") boolean lookbackActive,
            @Param("lookbackCutoff") LocalDateTime lookbackCutoff);

    @Query(value = """
            SELECT picked.strategy_bot_id AS strategyBotId, picked.id AS id
            FROM (
                SELECT r.strategy_bot_id,
                       r.id,
                       ROW_NUMBER() OVER (
                           PARTITION BY r.strategy_bot_id
                           ORDER BY r.requested_at DESC, r.id ASC
                       ) AS rn
                FROM strategy_bot_runs r
                WHERE r.strategy_bot_id IN (:strategyBotIds)
                  AND r.run_mode = 'FORWARD_TEST'
                  AND r.status = 'RUNNING'
                  AND (:runModeScope = 'ALL' OR r.run_mode = CAST(:runModeScope AS varchar))
                  AND (:lookbackActive = false OR COALESCE(r.completed_at, r.requested_at) >= :lookbackCutoff)
            ) picked
            WHERE picked.rn = 1
            """, nativeQuery = true)
    List<SelectedRunView> findActiveForwardRunIdsByStrategyBotIdIn(
            @Param("strategyBotIds") Collection<UUID> strategyBotIds,
            @Param("runModeScope") String runModeScope,
            @Param("lookbackActive") boolean lookbackActive,
            @Param("lookbackCutoff") LocalDateTime lookbackCutoff);

    @Query(value = """
            SELECT picked.strategy_bot_id AS strategyBotId, picked.id AS id
            FROM (
                SELECT r.strategy_bot_id,
                       r.id,
                       ROW_NUMBER() OVER (
                           PARTITION BY r.strategy_bot_id
                           ORDER BY r.requested_at DESC, r.id ASC
                       ) AS rn
                FROM strategy_bot_runs r
                WHERE r.strategy_bot_id IN (:strategyBotIds)
                  AND (:runModeScope = 'ALL' OR r.run_mode = CAST(:runModeScope AS varchar))
                  AND (:lookbackActive = false OR COALESCE(r.completed_at, r.requested_at) >= :lookbackCutoff)
            ) picked
            WHERE picked.rn <= :limitPerBot
            ORDER BY picked.strategy_bot_id, picked.rn
            """, nativeQuery = true)
    List<SelectedRunView> findRecentRunIdsByStrategyBotIdIn(
            @Param("strategyBotIds") Collection<UUID> strategyBotIds,
            @Param("runModeScope") String runModeScope,
            @Param("lookbackActive") boolean lookbackActive,
            @Param("lookbackCutoff") LocalDateTime lookbackCutoff,
            @Param("limitPerBot") int limitPerBot);

    @Query(value = """
            SELECT r.strategy_bot_id AS strategyBotId,
                   rc.reason AS reason,
                   SUM(COALESCE(NULLIF(rc.countText, '')::bigint, 0)) AS count
            FROM strategy_bot_runs r
            CROSS JOIN LATERAL jsonb_each_text(COALESCE(r.summary::jsonb -> 'entryReasonCounts', '{}'::jsonb)) rc(reason, countText)
            WHERE r.strategy_bot_id IN (:strategyBotIds)
              AND (:runModeScope = 'ALL' OR r.run_mode = CAST(:runModeScope AS varchar))
              AND (:lookbackActive = false OR COALESCE(r.completed_at, r.requested_at) >= :lookbackCutoff)
            GROUP BY r.strategy_bot_id, rc.reason
            """, nativeQuery = true)
    List<ReasonCountView> findEntryReasonCountsByStrategyBotIdIn(
            @Param("strategyBotIds") Collection<UUID> strategyBotIds,
            @Param("runModeScope") String runModeScope,
            @Param("lookbackActive") boolean lookbackActive,
            @Param("lookbackCutoff") LocalDateTime lookbackCutoff);

    @Query(value = """
            SELECT r.strategy_bot_id AS strategyBotId,
                   rc.reason AS reason,
                   SUM(COALESCE(NULLIF(rc.countText, '')::bigint, 0)) AS count
            FROM strategy_bot_runs r
            CROSS JOIN LATERAL jsonb_each_text(COALESCE(r.summary::jsonb -> 'exitReasonCounts', '{}'::jsonb)) rc(reason, countText)
            WHERE r.strategy_bot_id IN (:strategyBotIds)
              AND (:runModeScope = 'ALL' OR r.run_mode = CAST(:runModeScope AS varchar))
              AND (:lookbackActive = false OR COALESCE(r.completed_at, r.requested_at) >= :lookbackCutoff)
            GROUP BY r.strategy_bot_id, rc.reason
            """, nativeQuery = true)
    List<ReasonCountView> findExitReasonCountsByStrategyBotIdIn(
            @Param("strategyBotIds") Collection<UUID> strategyBotIds,
            @Param("runModeScope") String runModeScope,
            @Param("lookbackActive") boolean lookbackActive,
            @Param("lookbackCutoff") LocalDateTime lookbackCutoff);

    interface BoardAggregateView {
        UUID getStrategyBotId();
        Long getTotalRuns();
        Long getCompletedRuns();
        Long getRunningRuns();
        Long getFailedRuns();
        Long getCancelledRuns();
        Long getBacktestRuns();
        Long getForwardTestRuns();
        Long getCompilerReadyRuns();
        Long getPositiveCompletedRuns();
        Long getNegativeCompletedRuns();
        Long getTotalSimulatedTrades();
        Double getAvgReturnPercent();
        Double getAvgNetPnl();
        Double getAvgMaxDrawdownPercent();
        Double getAvgWinRate();
        Double getAvgTradeCount();
        Double getAvgProfitFactor();
        Double getAvgExpectancyPerTrade();
        LocalDateTime getLatestRequestedAt();
    }

    interface SelectedRunView {
        UUID getStrategyBotId();
        UUID getId();
    }

    interface ReasonCountView {
        UUID getStrategyBotId();
        String getReason();
        Long getCount();
    }
}
