package com.finance.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisPostRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be under 200 characters")
    private String title;

    @NotBlank(message = "Content is required")
    @Size(max = 5000, message = "Content must be under 5000 characters")
    private String content;

    @NotBlank(message = "Instrument symbol is required")
    private String instrumentSymbol;

    @NotNull(message = "Direction is required (BULLISH, BEARISH, NEUTRAL)")
    private String direction;

    /** Optional target price */
    private BigDecimal targetPrice;

    /** Optional stop price */
    private BigDecimal stopPrice;

    /** Optional: "1D", "1W", "1M", "3M" */
    private String timeframe;

    /** Optional: how many days until target should be evaluated */
    private Integer targetDays;
}
