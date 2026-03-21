package com.finance.core.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class StrategyBotRunRequest {
    private String runMode;
    private BigDecimal initialCapital;
    private LocalDate fromDate;
    private LocalDate toDate;
}
