package com.finance.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreference {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "dashboard_period", nullable = false)
    @Builder.Default
    private String dashboardPeriod = "1D";

    @Column(name = "dashboard_sort_by", nullable = false)
    @Builder.Default
    private String dashboardSortBy = "RETURN_PERCENTAGE";

    @Column(name = "dashboard_direction", nullable = false)
    @Builder.Default
    private String dashboardDirection = "DESC";

    @Column(name = "public_sort_by", nullable = false)
    @Builder.Default
    private String publicSortBy = "RETURN_PERCENTAGE";

    @Column(name = "public_direction", nullable = false)
    @Builder.Default
    private String publicDirection = "DESC";

    @Column(name = "terminal_market", nullable = false)
    @Builder.Default
    private String terminalMarket = "CRYPTO";

    @Column(name = "terminal_symbol", nullable = false)
    @Builder.Default
    private String terminalSymbol = "BTCUSDT";

    @Column(name = "terminal_compare_symbols", nullable = false, length = 512)
    @Builder.Default
    private String terminalCompareSymbols = "";

    @Column(name = "terminal_compare_visible", nullable = false)
    @Builder.Default
    private Boolean terminalCompareVisible = true;

    @Column(name = "terminal_range", nullable = false)
    @Builder.Default
    private String terminalRange = "1D";

    @Column(name = "terminal_interval", nullable = false)
    @Builder.Default
    private String terminalInterval = "1h";

    @Column(name = "terminal_favorite_symbols", nullable = false, length = 1024)
    @Builder.Default
    private String terminalFavoriteSymbols = "";

    @Column(name = "terminal_compare_baskets", nullable = false, length = 8192)
    @Builder.Default
    private String terminalCompareBaskets = "";

    @Column(name = "terminal_scanner_views", nullable = false, length = 8192)
    @Builder.Default
    private String terminalScannerViews = "";

    @Column(name = "notification_in_app_social", nullable = false)
    @Builder.Default
    private Boolean notificationInAppSocial = true;

    @Column(name = "notification_in_app_watchlist", nullable = false)
    @Builder.Default
    private Boolean notificationInAppWatchlist = true;

    @Column(name = "notification_in_app_tournaments", nullable = false)
    @Builder.Default
    private Boolean notificationInAppTournaments = true;

    @Column(name = "notification_digest_cadence", nullable = false)
    @Builder.Default
    private String notificationDigestCadence = "INSTANT";

    @Column(name = "notification_quiet_hours_enabled", nullable = false)
    @Builder.Default
    private Boolean notificationQuietHoursEnabled = false;

    @Column(name = "notification_quiet_hours_start", nullable = false, length = 5)
    @Builder.Default
    private String notificationQuietHoursStart = "22:00";

    @Column(name = "notification_quiet_hours_end", nullable = false, length = 5)
    @Builder.Default
    private String notificationQuietHoursEnd = "08:00";

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
