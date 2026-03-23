package com.finance.core.dto;

import com.finance.core.domain.StrategyBot;

import java.math.BigDecimal;
import java.util.UUID;

public record StrategyBotAgentDecisionContext(
        UUID botId,
        String market,
        String symbol,
        BigDecimal maxPositionSizePercent,
        BigDecimal configuredStopLossPercent,
        BigDecimal configuredTakeProfitPercent,
        boolean positionOpen) {

    public static StrategyBotAgentDecisionContext fromBot(StrategyBot bot, boolean positionOpen) {
        return new StrategyBotAgentDecisionContext(
                bot.getId(),
                bot.getMarket(),
                bot.getSymbol(),
                bot.getMaxPositionSizePercent(),
                bot.getStopLossPercent(),
                bot.getTakeProfitPercent(),
                positionOpen);
    }
}
