package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Robust tracking for user interactions like Likes and Comments.
 * Designed to scale with millions of rows.
 */
@Entity
@Table(name = "interactions", indexes = {
        @Index(name = "idx_interaction_actor", columnList = "actor_id"),
        @Index(name = "idx_interaction_target", columnList = "target_type, target_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interaction {

    public enum InteractionType {
        LIKE,
        COMMENT
    }

    public enum TargetType {
        PORTFOLIO,
        ANALYSIS_POST
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InteractionType interactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    /** For comments, store the payload here */
    @Column(length = 1000)
    private String content;

    @CreationTimestamp
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;
}
