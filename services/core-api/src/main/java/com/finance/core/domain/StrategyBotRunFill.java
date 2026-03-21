package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "strategy_bot_run_fills")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyBotRunFill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID strategyBotRunId;

    @Column(nullable = false)
    private Integer sequenceNo;

    @Column(nullable = false, length = 16)
    private String side;

    @Column(nullable = false)
    private Long openTime;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String matchedRules = "[]";

    @CreationTimestamp
    private LocalDateTime createdAt;
}
