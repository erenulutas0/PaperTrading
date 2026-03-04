package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a Paper Trading Tournament.
 * Users compete within a time window starting with equal balance.
 */
@Entity
@Table(name = "tournaments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tournament {

    public enum Status {
        UPCOMING, ACTIVE, COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name; // e.g. "February Weekly Challenge"

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal startingBalance = new BigDecimal("100000");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.UPCOMING;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    @Column(nullable = false)
    private LocalDateTime endsAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
