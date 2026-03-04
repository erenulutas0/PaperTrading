package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "portfolio_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Portfolio portfolio;

    @Column(nullable = false)
    private String symbol; // e.g., THYAO, BTCUSDT

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal averagePrice; // Basic cost basis tracking

    @Column
    @Builder.Default
    private Integer leverage = 1; // 1x by default

    @Column
    @Builder.Default
    private String side = "LONG"; // LONG or SHORT
}
