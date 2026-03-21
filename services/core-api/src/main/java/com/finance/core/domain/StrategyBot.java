package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "strategy_bots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyBot {

    public enum BotKind {
        RULE_BASED
    }

    public enum Status {
        DRAFT,
        READY,
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column
    private UUID linkedPortfolioId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private BotKind botKind = BotKind.RULE_BASED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(nullable = false, length = 32)
    private String market;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 16)
    private String timeframe;

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String entryRules = "{}";

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String exitRules = "{}";

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal maxPositionSizePercent;

    @Column(precision = 10, scale = 2)
    private BigDecimal stopLossPercent;

    @Column(precision = 10, scale = 2)
    private BigDecimal takeProfitPercent;

    @Column(nullable = false)
    @Builder.Default
    private Integer cooldownMinutes = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
