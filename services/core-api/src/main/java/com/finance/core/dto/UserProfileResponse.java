package com.finance.core.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserProfileResponse {
    private UUID id;
    private String username;
    private String displayName;
    private String bio;
    private String avatarUrl;
    private boolean verified;
    private int followerCount;
    private int followingCount;
    private int portfolioCount;
    private boolean isFollowing; // Whether the requesting user is following this user
    private double trustScore;
    private double winRate;
    private LocalDateTime memberSince;
}
