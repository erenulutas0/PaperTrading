package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "strategy_bot_run_equity_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StrategyBotRunEquityPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID strategyBotRunId;

    @Column(nullable = false)
    private Integer sequenceNo;

    @Column(nullable = false)
    private Long openTime;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal closePrice;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal equity;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
