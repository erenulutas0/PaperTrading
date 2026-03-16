package com.finance.core.repository;

import com.finance.core.domain.MarketChartNote;
import com.finance.core.dto.MarketType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketChartNoteRepository extends JpaRepository<MarketChartNote, UUID> {
    List<MarketChartNote> findByUserIdAndMarketAndSymbolOrderByPinnedDescCreatedAtDesc(UUID userId, MarketType market, String symbol);

    Page<MarketChartNote> findByUserIdAndMarketAndSymbolOrderByPinnedDescCreatedAtDesc(
            UUID userId,
            MarketType market,
            String symbol,
            Pageable pageable);

    Optional<MarketChartNote> findByIdAndUserId(UUID id, UUID userId);
}
