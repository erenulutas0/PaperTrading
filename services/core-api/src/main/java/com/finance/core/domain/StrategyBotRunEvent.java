package com.finance.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "strategy_bot_run_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyBotRunEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID strategyBotRunId;

    @Column(nullable = false)
    private Integer sequenceNo;

    @Column(nullable = false)
    private Long openTime;

    @Column(nullable = false, length = 32)
    private String phase;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal closePrice;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal cashBalance;

    @Column(nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal positionQuantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal equity;

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String matchedRules = "[]";

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String details = "{}";

    @CreationTimestamp
    private LocalDateTime createdAt;
}
