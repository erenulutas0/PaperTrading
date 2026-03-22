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
