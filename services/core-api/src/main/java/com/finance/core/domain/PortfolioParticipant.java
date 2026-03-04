package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks when a user "joins" a public portfolio.
 * Joining creates a clone of the portfolio for independent trading.
 */
@Entity
@Table(name = "portfolio_participants", uniqueConstraints = @UniqueConstraint(columnNames = { "portfolio_id",
        "user_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The original (source) portfolio being followed */
    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    /** The user who joined */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** The cloned portfolio created for this user */
    @Column(name = "cloned_portfolio_id")
    private UUID clonedPortfolioId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime joinedAt;
}
