package com.finance.core.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    // --- Social Profile Fields ---
    @Column
    private String displayName;

    @Column(length = 500)
    private String bio;

    @Column
    private String avatarUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(nullable = false)
    @Builder.Default
    private int followerCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private int followingCount = 0;

    /** Proprietary Proof-of-Performance metric, 0.0 to 100.0 */
    @Column(columnDefinition = "float(53) default 50.0")
    @Builder.Default
    private double trustScore = 50.0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
