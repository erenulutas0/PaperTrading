package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "watchlist_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watchlist_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Watchlist watchlist;

    @Column(nullable = false)
    private String symbol; // e.g. BTCUSDT

    /** Optional alert: notify user when price crosses this target */
    @Column
    private BigDecimal alertPriceAbove;

    /** Optional alert: notify user when price drops below this */
    @Column
    private BigDecimal alertPriceBelow;

    /** Has the above-alert already been triggered? (to avoid duplicates) */
    @Column
    @Builder.Default
    private Boolean alertAboveTriggered = false;

    /** Has the below-alert already been triggered? */
    @Column
    @Builder.Default
    private Boolean alertBelowTriggered = false;

    @Column
    private String notes; // User's personal notes on this symbol

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime addedAt;
}
