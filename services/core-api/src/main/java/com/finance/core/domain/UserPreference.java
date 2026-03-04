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

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

