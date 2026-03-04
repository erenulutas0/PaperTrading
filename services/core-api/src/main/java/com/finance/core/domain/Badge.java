package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Achievements / badges earned by users.
 * Awarded by the tournament system or other gamification triggers.
 */
@Entity
@Table(name = "badges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name; // e.g. "Top 10%", "Sharpe Master", "First Tournament"

    @Column(nullable = false)
    private String icon; // emoji or icon key

    @Column(length = 500)
    private String description;

    /** Optional: link back to the tournament that awarded this badge */
    @Column(name = "tournament_id")
    private UUID tournamentId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime earnedAt;
}
