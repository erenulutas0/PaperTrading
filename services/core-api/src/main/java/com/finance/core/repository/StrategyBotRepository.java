package com.finance.core.repository;

import com.finance.core.domain.Portfolio;
import com.finance.core.domain.StrategyBot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StrategyBotRepository extends JpaRepository<StrategyBot, UUID> {
    Page<StrategyBot> findByUserId(UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b FROM StrategyBot b
            LEFT JOIN StrategyBotRun r ON r.strategyBotId = b.id
            WHERE b.userId = :userId
            GROUP BY b
            ORDER BY COUNT(r) DESC, COALESCE(MAX(r.requestedAt), b.createdAt) DESC, b.createdAt DESC
            """,
            countQuery = """
                    SELECT COUNT(b) FROM StrategyBot b
                    WHERE b.userId = :userId
                    """)
    Page<StrategyBot> findByUserIdOrderByRunCountDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b FROM StrategyBot b
            LEFT JOIN StrategyBotRun r ON r.strategyBotId = b.id
            WHERE b.userId = :userId
            GROUP BY b
            ORDER BY COUNT(r) ASC, COALESCE(MAX(r.requestedAt), b.createdAt) ASC, b.createdAt ASC
            """,
            countQuery = """
                    SELECT COUNT(b) FROM StrategyBot b
                    WHERE b.userId = :userId
                    """)
    Page<StrategyBot> findByUserIdOrderByRunCountAsc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b FROM StrategyBot b
            LEFT JOIN StrategyBotRun r ON r.strategyBotId = b.id
            WHERE b.userId = :userId
            GROUP BY b
            ORDER BY COALESCE(MAX(r.requestedAt), b.createdAt) DESC, b.createdAt DESC
            """,
            countQuery = """
                    SELECT COUNT(b) FROM StrategyBot b
                    WHERE b.userId = :userId
                    """)
    Page<StrategyBot> findByUserIdOrderByLatestRequestedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b FROM StrategyBot b
            LEFT JOIN StrategyBotRun r ON r.strategyBotId = b.id
            WHERE b.userId = :userId
            GROUP BY b
            ORDER BY COALESCE(MAX(r.requestedAt), b.createdAt) ASC, b.createdAt ASC
            """,
            countQuery = """
                    SELECT COUNT(b) FROM StrategyBot b
                    WHERE b.userId = :userId
                    """)
    Page<StrategyBot> findByUserIdOrderByLatestRequestedAtAsc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY COUNT(r.id) DESC,
                     COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     b.created_at DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedRunCountDesc(@Param("userId") UUID userId,
                                                        @Param("runMode") String runMode,
                                                        @Param("lookbackActive") boolean lookbackActive,
                                                        @Param("cutoff") java.time.LocalDateTime cutoff,
                                                        Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY COUNT(r.id) ASC,
                     COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     b.created_at ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedRunCountAsc(@Param("userId") UUID userId,
                                                       @Param("runMode") String runMode,
                                                       @Param("lookbackActive") boolean lookbackActive,
                                                       @Param("cutoff") java.time.LocalDateTime cutoff,
                                                       Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     b.created_at DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedLatestRequestedAtDesc(@Param("userId") UUID userId,
                                                                 @Param("runMode") String runMode,
                                                                 @Param("lookbackActive") boolean lookbackActive,
                                                                 @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                 Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     b.created_at ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedLatestRequestedAtAsc(@Param("userId") UUID userId,
                                                                @Param("runMode") String runMode,
                                                                @Param("lookbackActive") boolean lookbackActive,
                                                                @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY COALESCE(SUM(COALESCE(NULLIF(r.summary::jsonb ->> 'tradeCount', '')::integer, 0)), 0) DESC,
                     COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedTotalSimulatedTradesDesc(@Param("userId") UUID userId,
                                                                    @Param("runMode") String runMode,
                                                                    @Param("lookbackActive") boolean lookbackActive,
                                                                    @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                    Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY COALESCE(SUM(COALESCE(NULLIF(r.summary::jsonb ->> 'tradeCount', '')::integer, 0)), 0) ASC,
                     COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedTotalSimulatedTradesAsc(@Param("userId") UUID userId,
                                                                   @Param("runMode") String runMode,
                                                                   @Param("lookbackActive") boolean lookbackActive,
                                                                   @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                   Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision) DESC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByAvgReturnDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision) DESC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedAvgReturnDesc(@Param("userId") UUID userId,
                                                         @Param("runMode") String runMode,
                                                         @Param("lookbackActive") boolean lookbackActive,
                                                         @Param("cutoff") java.time.LocalDateTime cutoff,
                                                         Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision) ASC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByAvgReturnAsc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision) ASC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedAvgReturnAsc(@Param("userId") UUID userId,
                                                        @Param("runMode") String runMode,
                                                        @Param("lookbackActive") boolean lookbackActive,
                                                        @Param("cutoff") java.time.LocalDateTime cutoff,
                                                        Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'netPnl', '')::double precision) DESC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByAvgNetPnlDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'netPnl', '')::double precision) DESC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedAvgNetPnlDesc(@Param("userId") UUID userId,
                                                         @Param("runMode") String runMode,
                                                         @Param("lookbackActive") boolean lookbackActive,
                                                         @Param("cutoff") java.time.LocalDateTime cutoff,
                                                         Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'netPnl', '')::double precision) ASC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByAvgNetPnlAsc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'netPnl', '')::double precision) ASC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedAvgNetPnlAsc(@Param("userId") UUID userId,
                                                        @Param("runMode") String runMode,
                                                        @Param("lookbackActive") boolean lookbackActive,
                                                        @Param("cutoff") java.time.LocalDateTime cutoff,
                                                        Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'winRate', '')::double precision) DESC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByAvgWinRateDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'winRate', '')::double precision) DESC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedAvgWinRateDesc(@Param("userId") UUID userId,
                                                          @Param("runMode") String runMode,
                                                          @Param("lookbackActive") boolean lookbackActive,
                                                          @Param("cutoff") java.time.LocalDateTime cutoff,
                                                          Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'winRate', '')::double precision) ASC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByAvgWinRateAsc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'winRate', '')::double precision) ASC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedAvgWinRateAsc(@Param("userId") UUID userId,
                                                         @Param("runMode") String runMode,
                                                         @Param("lookbackActive") boolean lookbackActive,
                                                         @Param("cutoff") java.time.LocalDateTime cutoff,
                                                         Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'profitFactor', '')::double precision) DESC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByAvgProfitFactorDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'profitFactor', '')::double precision) DESC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedAvgProfitFactorDesc(@Param("userId") UUID userId,
                                                               @Param("runMode") String runMode,
                                                               @Param("lookbackActive") boolean lookbackActive,
                                                               @Param("cutoff") java.time.LocalDateTime cutoff,
                                                               Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'profitFactor', '')::double precision) ASC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByAvgProfitFactorAsc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'profitFactor', '')::double precision) ASC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByScopedAvgProfitFactorAsc(@Param("userId") UUID userId,
                                                              @Param("runMode") String runMode,
                                                              @Param("lookbackActive") boolean lookbackActive,
                                                              @Param("cutoff") java.time.LocalDateTime cutoff,
                                                              Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name
            ORDER BY COALESCE(SUM(COALESCE(NULLIF(r.summary::jsonb ->> 'tradeCount', '')::integer, 0)), 0) DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByTotalSimulatedTradesDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE b.user_id = :userId
            GROUP BY b.id, b.name
            ORDER BY COALESCE(SUM(COALESCE(NULLIF(r.summary::jsonb ->> 'tradeCount', '')::integer, 0)), 0) ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    WHERE b.user_id = :userId
                    """,
            nativeQuery = true)
    Page<UUID> findOwnedBotIdsOrderByTotalSimulatedTradesAsc(@Param("userId") UUID userId, Pageable pageable);

    Optional<StrategyBot> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT b FROM StrategyBot b
            JOIN Portfolio p ON p.id = b.linkedPortfolioId
            LEFT JOIN AppUser u ON u.id = b.userId
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            """)
    List<StrategyBot> findPublicDiscoverableBots(
            @Param("visibility") Portfolio.Visibility visibility,
            @Param("excludedStatus") StrategyBot.Status excludedStatus,
            @Param("query") String query);

    @Query(value = """
            SELECT b FROM StrategyBot b
            JOIN Portfolio p ON p.id = b.linkedPortfolioId
            LEFT JOIN AppUser u ON u.id = b.userId
            LEFT JOIN StrategyBotRun r ON r.strategyBotId = b.id
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b
            ORDER BY COUNT(r) DESC, COALESCE(MAX(r.requestedAt), b.createdAt) DESC, b.createdAt DESC
            """,
            countQuery = """
                    SELECT COUNT(b) FROM StrategyBot b
                    JOIN Portfolio p ON p.id = b.linkedPortfolioId
                    LEFT JOIN AppUser u ON u.id = b.userId
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """)
    Page<StrategyBot> findPublicDiscoverableBotsOrderByRunCountDesc(
            @Param("visibility") Portfolio.Visibility visibility,
            @Param("excludedStatus") StrategyBot.Status excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b FROM StrategyBot b
            JOIN Portfolio p ON p.id = b.linkedPortfolioId
            LEFT JOIN AppUser u ON u.id = b.userId
            LEFT JOIN StrategyBotRun r ON r.strategyBotId = b.id
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b
            ORDER BY COUNT(r) ASC, COALESCE(MAX(r.requestedAt), b.createdAt) ASC, b.createdAt ASC
            """,
            countQuery = """
                    SELECT COUNT(b) FROM StrategyBot b
                    JOIN Portfolio p ON p.id = b.linkedPortfolioId
                    LEFT JOIN AppUser u ON u.id = b.userId
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """)
    Page<StrategyBot> findPublicDiscoverableBotsOrderByRunCountAsc(
            @Param("visibility") Portfolio.Visibility visibility,
            @Param("excludedStatus") StrategyBot.Status excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b FROM StrategyBot b
            JOIN Portfolio p ON p.id = b.linkedPortfolioId
            LEFT JOIN AppUser u ON u.id = b.userId
            LEFT JOIN StrategyBotRun r ON r.strategyBotId = b.id
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b
            ORDER BY COALESCE(MAX(r.requestedAt), b.createdAt) DESC, b.createdAt DESC
            """,
            countQuery = """
                    SELECT COUNT(b) FROM StrategyBot b
                    JOIN Portfolio p ON p.id = b.linkedPortfolioId
                    LEFT JOIN AppUser u ON u.id = b.userId
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """)
    Page<StrategyBot> findPublicDiscoverableBotsOrderByLatestRequestedAtDesc(
            @Param("visibility") Portfolio.Visibility visibility,
            @Param("excludedStatus") StrategyBot.Status excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b FROM StrategyBot b
            JOIN Portfolio p ON p.id = b.linkedPortfolioId
            LEFT JOIN AppUser u ON u.id = b.userId
            LEFT JOIN StrategyBotRun r ON r.strategyBotId = b.id
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b
            ORDER BY COALESCE(MAX(r.requestedAt), b.createdAt) ASC, b.createdAt ASC
            """,
            countQuery = """
                    SELECT COUNT(b) FROM StrategyBot b
                    JOIN Portfolio p ON p.id = b.linkedPortfolioId
                    LEFT JOIN AppUser u ON u.id = b.userId
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.displayName, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """)
    Page<StrategyBot> findPublicDiscoverableBotsOrderByLatestRequestedAtAsc(
            @Param("visibility") Portfolio.Visibility visibility,
            @Param("excludedStatus") StrategyBot.Status excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY COUNT(r.id) DESC,
                     COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     b.created_at DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedRunCountDesc(@Param("visibility") String visibility,
                                                                     @Param("excludedStatus") String excludedStatus,
                                                                     @Param("query") String query,
                                                                     @Param("runMode") String runMode,
                                                                     @Param("lookbackActive") boolean lookbackActive,
                                                                     @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                     Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY COUNT(r.id) ASC,
                     COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     b.created_at ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedRunCountAsc(@Param("visibility") String visibility,
                                                                    @Param("excludedStatus") String excludedStatus,
                                                                    @Param("query") String query,
                                                                    @Param("runMode") String runMode,
                                                                    @Param("lookbackActive") boolean lookbackActive,
                                                                    @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                    Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     b.created_at DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedLatestRequestedAtDesc(@Param("visibility") String visibility,
                                                                               @Param("excludedStatus") String excludedStatus,
                                                                               @Param("query") String query,
                                                                               @Param("runMode") String runMode,
                                                                               @Param("lookbackActive") boolean lookbackActive,
                                                                               @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                               Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     b.created_at ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedLatestRequestedAtAsc(@Param("visibility") String visibility,
                                                                              @Param("excludedStatus") String excludedStatus,
                                                                              @Param("query") String query,
                                                                              @Param("runMode") String runMode,
                                                                              @Param("lookbackActive") boolean lookbackActive,
                                                                              @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                              Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY COALESCE(SUM(COALESCE(NULLIF(r.summary::jsonb ->> 'tradeCount', '')::integer, 0)), 0) DESC,
                     COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedTotalSimulatedTradesDesc(@Param("visibility") String visibility,
                                                                                  @Param("excludedStatus") String excludedStatus,
                                                                                  @Param("query") String query,
                                                                                  @Param("runMode") String runMode,
                                                                                  @Param("lookbackActive") boolean lookbackActive,
                                                                                  @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                                  Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY COALESCE(SUM(COALESCE(NULLIF(r.summary::jsonb ->> 'tradeCount', '')::integer, 0)), 0) ASC,
                     COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedTotalSimulatedTradesAsc(@Param("visibility") String visibility,
                                                                                 @Param("excludedStatus") String excludedStatus,
                                                                                 @Param("query") String query,
                                                                                 @Param("runMode") String runMode,
                                                                                 @Param("lookbackActive") boolean lookbackActive,
                                                                                 @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                                 Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision) DESC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByAvgReturnDesc(
            @Param("visibility") String visibility,
            @Param("excludedStatus") String excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision) DESC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedAvgReturnDesc(@Param("visibility") String visibility,
                                                                      @Param("excludedStatus") String excludedStatus,
                                                                      @Param("query") String query,
                                                                      @Param("runMode") String runMode,
                                                                      @Param("lookbackActive") boolean lookbackActive,
                                                                      @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                      Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision) ASC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByAvgReturnAsc(
            @Param("visibility") String visibility,
            @Param("excludedStatus") String excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'returnPercent', '')::double precision) ASC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedAvgReturnAsc(@Param("visibility") String visibility,
                                                                     @Param("excludedStatus") String excludedStatus,
                                                                     @Param("query") String query,
                                                                     @Param("runMode") String runMode,
                                                                     @Param("lookbackActive") boolean lookbackActive,
                                                                     @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                     Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'netPnl', '')::double precision) DESC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByAvgNetPnlDesc(
            @Param("visibility") String visibility,
            @Param("excludedStatus") String excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'netPnl', '')::double precision) DESC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedAvgNetPnlDesc(@Param("visibility") String visibility,
                                                                      @Param("excludedStatus") String excludedStatus,
                                                                      @Param("query") String query,
                                                                      @Param("runMode") String runMode,
                                                                      @Param("lookbackActive") boolean lookbackActive,
                                                                      @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                      Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'netPnl', '')::double precision) ASC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByAvgNetPnlAsc(
            @Param("visibility") String visibility,
            @Param("excludedStatus") String excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'netPnl', '')::double precision) ASC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedAvgNetPnlAsc(@Param("visibility") String visibility,
                                                                     @Param("excludedStatus") String excludedStatus,
                                                                     @Param("query") String query,
                                                                     @Param("runMode") String runMode,
                                                                     @Param("lookbackActive") boolean lookbackActive,
                                                                     @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                     Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'winRate', '')::double precision) DESC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByAvgWinRateDesc(
            @Param("visibility") String visibility,
            @Param("excludedStatus") String excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'winRate', '')::double precision) DESC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedAvgWinRateDesc(@Param("visibility") String visibility,
                                                                       @Param("excludedStatus") String excludedStatus,
                                                                       @Param("query") String query,
                                                                       @Param("runMode") String runMode,
                                                                       @Param("lookbackActive") boolean lookbackActive,
                                                                       @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                       Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'winRate', '')::double precision) ASC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByAvgWinRateAsc(
            @Param("visibility") String visibility,
            @Param("excludedStatus") String excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'winRate', '')::double precision) ASC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedAvgWinRateAsc(@Param("visibility") String visibility,
                                                                      @Param("excludedStatus") String excludedStatus,
                                                                      @Param("query") String query,
                                                                      @Param("runMode") String runMode,
                                                                      @Param("lookbackActive") boolean lookbackActive,
                                                                      @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                      Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'profitFactor', '')::double precision) DESC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByAvgProfitFactorDesc(
            @Param("visibility") String visibility,
            @Param("excludedStatus") String excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'profitFactor', '')::double precision) DESC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedAvgProfitFactorDesc(@Param("visibility") String visibility,
                                                                            @Param("excludedStatus") String excludedStatus,
                                                                            @Param("query") String query,
                                                                            @Param("runMode") String runMode,
                                                                            @Param("lookbackActive") boolean lookbackActive,
                                                                            @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                            Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'profitFactor', '')::double precision) ASC NULLS LAST,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByAvgProfitFactorAsc(
            @Param("visibility") String visibility,
            @Param("excludedStatus") String excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
             AND (:runMode = 'ALL' OR r.run_mode = :runMode)
             AND (:lookbackActive = FALSE OR COALESCE(r.completed_at, r.requested_at) >= :cutoff)
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name, b.created_at
            ORDER BY AVG(NULLIF(r.summary::jsonb ->> 'profitFactor', '')::double precision) ASC NULLS LAST,
                     COALESCE(MAX(r.requested_at), b.created_at) ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByScopedAvgProfitFactorAsc(@Param("visibility") String visibility,
                                                                           @Param("excludedStatus") String excludedStatus,
                                                                           @Param("query") String query,
                                                                           @Param("runMode") String runMode,
                                                                           @Param("lookbackActive") boolean lookbackActive,
                                                                           @Param("cutoff") java.time.LocalDateTime cutoff,
                                                                           Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name
            ORDER BY COALESCE(SUM(COALESCE(NULLIF(r.summary::jsonb ->> 'tradeCount', '')::integer, 0)), 0) DESC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByTotalSimulatedTradesDesc(
            @Param("visibility") String visibility,
            @Param("excludedStatus") String excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = """
            SELECT b.id
            FROM strategy_bots b
            JOIN portfolios p
              ON p.id = b.linked_portfolio_id
            LEFT JOIN users u
              ON u.id = b.user_id
            LEFT JOIN strategy_bot_runs r
              ON r.strategy_bot_id = b.id
             AND r.status = 'COMPLETED'
            WHERE p.visibility = :visibility
              AND b.status <> :excludedStatus
              AND (
                :query = ''
                OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            GROUP BY b.id, b.name
            ORDER BY COALESCE(SUM(COALESCE(NULLIF(r.summary::jsonb ->> 'tradeCount', '')::integer, 0)), 0) ASC,
                     LOWER(b.name) ASC,
                     b.id ASC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM strategy_bots b
                    JOIN portfolios p
                      ON p.id = b.linked_portfolio_id
                    LEFT JOIN users u
                      ON u.id = b.user_id
                    WHERE p.visibility = :visibility
                      AND b.status <> :excludedStatus
                      AND (
                        :query = ''
                        OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.market) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(b.timeframe) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.username, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(u.display_name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<UUID> findPublicDiscoverableBotIdsOrderByTotalSimulatedTradesAsc(
            @Param("visibility") String visibility,
            @Param("excludedStatus") String excludedStatus,
            @Param("query") String query,
            Pageable pageable);

    @Query("""
            SELECT b FROM StrategyBot b
            JOIN Portfolio p ON p.id = b.linkedPortfolioId
            WHERE b.id = :botId
              AND p.visibility = :visibility
              AND b.status <> :excludedStatus
            """)
    Optional<StrategyBot> findPublicDiscoverableBotById(
            @Param("botId") UUID botId,
            @Param("visibility") Portfolio.Visibility visibility,
            @Param("excludedStatus") StrategyBot.Status excludedStatus);
}
