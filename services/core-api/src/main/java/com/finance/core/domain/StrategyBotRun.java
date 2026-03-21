package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "strategy_bot_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyBotRun {

    public enum RunMode {
        BACKTEST,
        FORWARD_TEST
    }

    public enum Status {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID strategyBotId;

    @Column(nullable = false)
    private UUID userId;

    @Column
    private UUID linkedPortfolioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunMode runMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private Status status = Status.QUEUED;

    @Column(precision = 18, scale = 2)
    private BigDecimal requestedInitialCapital;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal effectiveInitialCapital;

    @Column
    private LocalDate fromDate;

    @Column
    private LocalDate toDate;

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String compiledEntryRules = "{}";

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String compiledExitRules = "{}";

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String summary = "{}";

    @Column(length = 1000)
    private String errorMessage;

    @CreationTimestamp
    private LocalDateTime requestedAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;
}
