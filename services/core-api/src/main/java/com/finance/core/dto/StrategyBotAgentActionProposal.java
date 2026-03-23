package com.finance.core.dto;

import java.math.BigDecimal;
import java.util.List;

public record StrategyBotAgentActionProposal(
        StrategyBotAgentActionType action,
        BigDecimal sizePercent,
        BigDecimal closePercent,
        BigDecimal stopLossPercent,
        BigDecimal takeProfitPercent,
        String rationale,
        List<String> matchedSignals,
        List<StrategyBotAgentToolScope> toolScopes,
        String providerName,
        String providerModel,
        String promptVersion,
        String providerResponseId) {

    public StrategyBotAgentActionProposal {
        matchedSignals = matchedSignals == null ? List.of() : List.copyOf(matchedSignals);
        toolScopes = toolScopes == null ? List.of() : List.copyOf(toolScopes);
    }
}
