package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "watchlist_alert_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchlistAlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watchlist_item_id", nullable = false)
    private WatchlistItem watchlistItem;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WatchlistAlertDirection direction;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal thresholdPrice;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal triggeredPrice;

    @Column(length = 512)
    private String message;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime triggeredAt;
}
