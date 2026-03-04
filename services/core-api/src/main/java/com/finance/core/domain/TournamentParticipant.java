package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks a user's participation in a tournament.
 * Links to a dedicated portfolio created for the tournament.
 */
@Entity
@Table(name = "tournament_participants", uniqueConstraints = @UniqueConstraint(columnNames = { "tournament_id",
        "user_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tournament_id", nullable = false)
    private UUID tournamentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** The dedicated portfolio created for this tournament entry */
    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    /** Final rank when tournament completes (null while active) */
    @Column
    private Integer finalRank;

    /** Final return % when tournament completes */
    @Column
    private Double finalReturnPercent;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime joinedAt;
}
