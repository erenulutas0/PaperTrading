package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_user", columnList = "user_id, is_read")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    public enum NotificationType {
        FOLLOW,
        PORTFOLIO_LIKE,
        POST_LIKE,
        PORTFOLIO_COMMENT,
        POST_COMMENT,
        PORTFOLIO_JOINED,
        PRICE_ALERT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The user who receives the notification
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // The user who triggered the notification
    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_username", length = 50)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    // Optional reference (e.g., Portfolio ID or Post ID)
    @Column(name = "reference_id")
    private UUID referenceId;

    // E.g. "My Growth Portfolio" or "BTC Bull Run"
    @Column(name = "reference_label", length = 255)
    private String referenceLabel;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @CreationTimestamp
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;
}
