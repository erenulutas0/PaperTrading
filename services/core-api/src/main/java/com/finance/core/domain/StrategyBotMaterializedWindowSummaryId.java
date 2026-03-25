package com.finance.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class StrategyBotMaterializedWindowSummaryId implements Serializable {

    @Column(name = "strategy_bot_id", nullable = false)
    private UUID strategyBotId;

    @Column(name = "run_mode_scope", nullable = false, length = 32)
    private String runModeScope;

    @Column(name = "lookback_days", nullable = false)
    private Integer lookbackDays;
}
