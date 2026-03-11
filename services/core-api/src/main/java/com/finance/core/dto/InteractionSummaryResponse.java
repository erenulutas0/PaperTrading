package com.finance.core.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InteractionSummaryResponse {
    long likeCount;
    boolean hasLiked;
    long commentCount;
}
