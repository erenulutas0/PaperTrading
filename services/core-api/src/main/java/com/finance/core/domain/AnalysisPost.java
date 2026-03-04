package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable analysis post. Once created, it cannot be edited — only
 * soft-deleted.
 * This is the core anti-manipulation mechanism: users cannot silently delete
 * wrong predictions or backdate their analysis.
 */
@Entity
@Table(name = "analysis_posts", indexes = {
        @Index(name = "idx_analysispost_author", columnList = "author_id"),
        @Index(name = "idx_analysispost_outcome", columnList = "outcome"),
        @Index(name = "idx_analysispost_created", columnList = "createdAt"),
        @Index(name = "idx_analysispost_deleted", columnList = "deleted"),
        @Index(name = "idx_analysispost_instrument", columnList = "instrumentSymbol")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisPost {

    public enum Direction {
        BULLISH, BEARISH, NEUTRAL
    }

    public enum Outcome {
        PENDING, HIT, MISSED, EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 5000)
    private String content;

    /** Which instrument this analysis is about (e.g. "BTCUSDT") */
    @Column(nullable = false)
    private String instrumentSymbol;

    /** BULLISH / BEARISH / NEUTRAL */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    /** Target price the author predicts the instrument will reach */
    @Column(precision = 20, scale = 8)
    private BigDecimal targetPrice;

    /** Stop-loss price */
    @Column(precision = 20, scale = 8)
    private BigDecimal stopPrice;

    /** Timeframe hint: "1D", "1W", "1M", "3M" */
    @Column(length = 10)
    private String timeframe;

    /**
     * Deadline for target resolution. If price doesn't hit target by this date,
     * outcome = EXPIRED
     */
    private LocalDateTime targetDate;

    /** The market price at the moment this post was created — immutable proof */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal priceAtCreation;

    /** System-resolved outcome */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Outcome outcome = Outcome.PENDING;

    /** When the system resolved the outcome */
    private LocalDateTime outcomeResolvedAt;

    /** Price at which the outcome was resolved */
    @Column(precision = 20, scale = 8)
    private BigDecimal priceAtResolution;

    // --- Soft delete (tombstone pattern) ---
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    private LocalDateTime deletedAt;

    // --- Timestamps (server-only, immutable) ---
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
