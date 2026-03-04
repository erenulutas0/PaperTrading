package com.finance.core.dto;

import lombok.Data;

@Data
public class InteractionRequest {
    private String targetType; // PORTFOLIO, ANALYSIS_POST
    private String content; // Null for LIKE, text for COMMENT
}
