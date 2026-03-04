package com.finance.core.service;

import com.finance.core.domain.PortfolioSnapshot;
import com.finance.core.repository.PortfolioSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional // Rolls back the database changes after each test automatically
class AnalyticsIntegrationTest {

    @Autowired
    private PerformanceAnalyticsService performanceAnalyticsService;

    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;

    @Test
    void testMaxDrawdownIntegration() {
        UUID portfolioId = UUID.randomUUID();

        // Save some snapshots to the real database
        snapshotRepository.save(createSnapshot(portfolioId, 1000.0, LocalDateTime.now().minusDays(5)));
        snapshotRepository.save(createSnapshot(portfolioId, 1200.0, LocalDateTime.now().minusDays(4))); // Peak
        snapshotRepository.save(createSnapshot(portfolioId, 900.0, LocalDateTime.now().minusDays(3))); // Trough ->
                                                                                                       // Drawdown: 25%
        snapshotRepository.save(createSnapshot(portfolioId, 1500.0, LocalDateTime.now().minusDays(2)));
        snapshotRepository.save(createSnapshot(portfolioId, 1350.0, LocalDateTime.now().minusDays(1))); // 10% drawdown
                                                                                                        // from 1500

        // The maximum drawdown should be 25%
        double mdd = performanceAnalyticsService.calculateMaxDrawdown(portfolioId);

        assertEquals(25.0, mdd, 0.001);
    }

    private PortfolioSnapshot createSnapshot(UUID pId, double equity, LocalDateTime time) {
        return PortfolioSnapshot.builder()
                .portfolioId(pId)
                .totalEquity(BigDecimal.valueOf(equity))
                .timestamp(time)
                .build();
    }
}
