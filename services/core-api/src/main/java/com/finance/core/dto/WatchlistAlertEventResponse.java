package com.finance.core.dto;

import com.finance.core.domain.WatchlistAlertDirection;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class WatchlistAlertEventResponse {
    UUID id;
    UUID watchlistItemId;
    String symbol;
    WatchlistAlertDirection direction;
    BigDecimal thresholdPrice;
    BigDecimal triggeredPrice;
    String message;
    LocalDateTime triggeredAt;
}
