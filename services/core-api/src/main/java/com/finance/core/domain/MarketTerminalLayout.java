package com.finance.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "market_terminal_layouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketTerminalLayout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column
    private UUID watchlistId;

    @Column(nullable = false, length = 32)
    private String market;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 512)
    @Builder.Default
    private String compareSymbols = "";

    @Column(nullable = false)
    @Builder.Default
    private Boolean compareVisible = true;

    @Column(name = "chart_range", nullable = false, length = 16)
    private String range;

    @Column(name = "chart_interval", nullable = false, length = 16)
    private String interval;

    @Column(nullable = false, length = 1024)
    @Builder.Default
    private String favoriteSymbols = "";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
