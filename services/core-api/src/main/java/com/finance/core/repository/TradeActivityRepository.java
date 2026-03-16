package com.finance.core.repository;

import com.finance.core.domain.TradeActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;
import java.math.BigDecimal;

@Repository
public interface TradeActivityRepository extends JpaRepository<TradeActivity, UUID> {
    List<TradeActivity> findByPortfolioIdOrderByTimestampDesc(UUID portfolioId);

    Page<TradeActivity> findByPortfolioIdOrderByTimestampDesc(UUID portfolioId, Pageable pageable);

    @Query("SELECT t FROM TradeActivity t WHERE t.portfolioId IN :portfolioIds ORDER BY t.timestamp DESC")
    List<TradeActivity> findRecentTradesForPortfolios(@Param("portfolioIds") List<UUID> portfolioIds,
            Pageable pageable);

    long countByPortfolioIdIn(Collection<UUID> portfolioIds);

    @Query("SELECT COUNT(t) FROM TradeActivity t WHERE t.portfolioId IN :portfolioIds AND t.realizedPnl > 0")
    long countProfitableRealizedTrades(@Param("portfolioIds") Collection<UUID> portfolioIds);

    @Query("SELECT COUNT(t) FROM TradeActivity t WHERE t.portfolioId IN :portfolioIds AND t.realizedPnl < 0")
    long countLosingRealizedTrades(@Param("portfolioIds") Collection<UUID> portfolioIds);

    @Query("SELECT COALESCE(SUM(t.realizedPnl), 0) FROM TradeActivity t WHERE t.portfolioId IN :portfolioIds AND t.realizedPnl IS NOT NULL")
    BigDecimal sumRealizedPnl(@Param("portfolioIds") Collection<UUID> portfolioIds);
}
