package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Captures social events for the activity feed.
 * Events are append-only and immutable.
 */
@Entity
@Table(name = "activity_events", indexes = {
        @Index(name = "idx_activity_actor", columnList = "actor_id"),
        @Index(name = "idx_activity_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityEvent {

    public enum EventType {
        FOLLOW,
        POST_CREATED,
        PORTFOLIO_PUBLISHED,
        PORTFOLIO_JOINED,
        PORTFOLIO_LEFT,
        PORTFOLIO_LIKED,
        PORTFOLIO_COMMENTED,
        POST_LIKED,
        POST_COMMENTED,
        TRADE_EXECUTED,
        POST_DELETED
    }

    public enum TargetType {
        USER, PORTFOLIO, POST
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_username")
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    /** Human-friendly label, e.g. "BTCUSDT Analysis" or "Growth Portfolio" */
    @Column(name = "target_label")
    private String targetLabel;

    /** Optional JSON metadata for extra context */
    @Column(length = 2000)
    private String metadata;

    @CreationTimestamp
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;
}
