package com.finance.core.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserSuggestionResponse {
    private UUID id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private boolean verified;
    private int followerCount;
    private int portfolioCount;
    private double trustScore;
    private boolean following;
}
