package com.finance.core.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TrustScoreHistoryPointResponse {
    private LocalDateTime capturedAt;
    private double trustScore;
    private double winRate;
}
