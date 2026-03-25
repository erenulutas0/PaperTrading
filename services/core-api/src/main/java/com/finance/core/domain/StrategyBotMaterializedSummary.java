package com.finance.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "strategy_bot_materialized_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyBotMaterializedSummary implements Persistable<UUID> {

    @Id
    @Column(nullable = false)
    private UUID strategyBotId;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalRuns = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer backtestRuns = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer forwardTestRuns = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer completedRuns = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer runningRuns = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer failedRuns = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer cancelledRuns = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer compilerReadyRuns = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer positiveCompletedRuns = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer negativeCompletedRuns = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalSimulatedTrades = 0;

    @Column
    private Double avgReturnPercent;

    @Column
    private Double avgNetPnl;

    @Column
    private Double avgMaxDrawdownPercent;

    @Column
    private Double avgWinRate;

    @Column
    private Double avgTradeCount;

    @Column
    private Double avgProfitFactor;

    @Column
    private Double avgExpectancyPerTrade;

    @Column
    private LocalDateTime latestRequestedAt;

    @Column
    private UUID bestRunId;

    @Column
    private UUID worstRunId;

    @Column
    private UUID latestCompletedRunId;

    @Column
    private UUID activeForwardRunId;

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String recentRunIds = "[]";

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String entryDriverTotals = "{}";

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String exitDriverTotals = "{}";

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Transient
    @Override
    public UUID getId() {
        return strategyBotId;
    }

    @Transient
    @Override
    public boolean isNew() {
        return createdAt == null;
    }
}
