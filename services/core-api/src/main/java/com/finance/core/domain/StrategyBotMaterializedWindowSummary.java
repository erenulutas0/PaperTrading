package com.finance.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "strategy_bot_materialized_window_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyBotMaterializedWindowSummary {

    @EmbeddedId
    private StrategyBotMaterializedWindowSummaryId id;

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

    public UUID getStrategyBotId() {
        return id == null ? null : id.getStrategyBotId();
    }

    public String getRunModeScope() {
        return id == null ? null : id.getRunModeScope();
    }

    public Integer getLookbackDays() {
        return id == null ? null : id.getLookbackDays();
    }
}
