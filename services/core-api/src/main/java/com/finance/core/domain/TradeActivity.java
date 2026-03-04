package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trade_activities", indexes = {
        @Index(name = "idx_trade_portfolio_time", columnList = "portfolioId, timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID portfolioId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String type; // BUY, SELL, LIQUIDATION

    @Column(nullable = false)
    private String side; // LONG, SHORT

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal price;

    @Column
    private BigDecimal realizedPnl;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
