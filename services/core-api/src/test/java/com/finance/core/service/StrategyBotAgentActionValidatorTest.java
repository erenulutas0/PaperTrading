package com.finance.core.service;

import com.finance.core.dto.StrategyBotAgentActionProposal;
import com.finance.core.dto.StrategyBotAgentActionType;
import com.finance.core.dto.StrategyBotAgentDecisionContext;
import com.finance.core.dto.StrategyBotAgentToolScope;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StrategyBotAgentActionValidatorTest {

    private final StrategyBotAgentActionValidator validator = new StrategyBotAgentActionValidator();

    @Test
    void validate_acceptsBoundedBuyProposalWithinBotCap() {
        StrategyBotAgentDecisionContext context = context(false, "25", "5", "12");

        StrategyBotAgentActionProposal proposal = proposalBuilder(StrategyBotAgentActionType.BUY)
                .sizePercent(new BigDecimal("20"))
                .stopLossPercent(new BigDecimal("4"))
                .takeProfitPercent(new BigDecimal("10"))
                .build();

        assertDoesNotThrow(() -> validator.validate(context, proposal));
    }

    @Test
    void validate_rejectsBuyThatExceedsBotCap() {
        StrategyBotAgentDecisionContext context = context(false, "25", "5", "12");

        StrategyBotAgentActionValidator.ValidationException error = assertThrows(StrategyBotAgentActionValidator.ValidationException.class, () ->
                validator.validate(context, proposalBuilder(StrategyBotAgentActionType.BUY)
                        .sizePercent(new BigDecimal("30"))
                        .build()));

        assertEquals("strategy_bot_agent_buy_size_exceeds_cap", error.code());
    }

    @Test
    void validate_rejectsBuyWhenPositionAlreadyOpen() {
        StrategyBotAgentDecisionContext context = context(true, "25", "5", "12");

        StrategyBotAgentActionValidator.ValidationException error = assertThrows(StrategyBotAgentActionValidator.ValidationException.class, () ->
                validator.validate(context, proposalBuilder(StrategyBotAgentActionType.BUY)
                        .sizePercent(new BigDecimal("10"))
                        .build()));

        assertEquals("strategy_bot_agent_buy_requires_flat_position", error.code());
    }

    @Test
    void validate_acceptsStopUpdateInsideConfiguredRiskEnvelope() {
        StrategyBotAgentDecisionContext context = context(true, "25", "5", "12");

        StrategyBotAgentActionProposal proposal = proposalBuilder(StrategyBotAgentActionType.UPDATE_STOPS)
                .stopLossPercent(new BigDecimal("3"))
                .takeProfitPercent(new BigDecimal("8"))
                .build();

        assertDoesNotThrow(() -> validator.validate(context, proposal));
    }

    @Test
    void validate_rejectsStopUpdateWhenBotHasNoStopLossConfigured() {
        StrategyBotAgentDecisionContext context = context(true, "25", null, "12");

        StrategyBotAgentActionValidator.ValidationException error = assertThrows(StrategyBotAgentActionValidator.ValidationException.class, () ->
                validator.validate(context, proposalBuilder(StrategyBotAgentActionType.UPDATE_STOPS)
                        .stopLossPercent(new BigDecimal("3"))
                        .build()));

        assertEquals("strategy_bot_agent_stop_loss_not_configured", error.code());
    }

    @Test
    void validate_rejectsSellWithoutOpenPosition() {
        StrategyBotAgentDecisionContext context = context(false, "25", "5", "12");

        StrategyBotAgentActionValidator.ValidationException error = assertThrows(StrategyBotAgentActionValidator.ValidationException.class, () ->
                validator.validate(context, proposalBuilder(StrategyBotAgentActionType.SELL)
                        .closePercent(new BigDecimal("100"))
                        .build()));

        assertEquals("strategy_bot_agent_sell_requires_open_position", error.code());
    }

    @Test
    void validate_rejectsHoldPayloadWithPositionMutationFields() {
        StrategyBotAgentDecisionContext context = context(false, "25", "5", "12");

        StrategyBotAgentActionValidator.ValidationException error = assertThrows(StrategyBotAgentActionValidator.ValidationException.class, () ->
                validator.validate(context, proposalBuilder(StrategyBotAgentActionType.HOLD)
                        .sizePercent(new BigDecimal("5"))
                        .build()));

        assertEquals("strategy_bot_agent_hold_payload_invalid", error.code());
    }

    @Test
    void validate_rejectsMissingAuditMetadata() {
        StrategyBotAgentDecisionContext context = context(false, "25", "5", "12");

        StrategyBotAgentActionValidator.ValidationException error = assertThrows(StrategyBotAgentActionValidator.ValidationException.class, () ->
                validator.validate(context, new StrategyBotAgentActionProposal(
                        StrategyBotAgentActionType.HOLD,
                        null,
                        null,
                        null,
                        null,
                        " ",
                        List.of(),
                        List.of(StrategyBotAgentToolScope.MARKET_CANDLES),
                        "openai",
                        "gpt",
                        "v1",
                        null)));

        assertEquals("strategy_bot_agent_rationale_required", error.code());
    }

    private StrategyBotAgentDecisionContext context(
            boolean positionOpen,
            String maxPositionSizePercent,
            String stopLossPercent,
            String takeProfitPercent) {
        return new StrategyBotAgentDecisionContext(
                UUID.randomUUID(),
                "CRYPTO",
                "BTCUSDT",
                decimal(maxPositionSizePercent),
                decimal(stopLossPercent),
                decimal(takeProfitPercent),
                positionOpen);
    }

    private ProposalBuilder proposalBuilder(StrategyBotAgentActionType actionType) {
        return new ProposalBuilder(actionType);
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static final class ProposalBuilder {
        private final StrategyBotAgentActionType action;
        private BigDecimal sizePercent;
        private BigDecimal closePercent;
        private BigDecimal stopLossPercent;
        private BigDecimal takeProfitPercent;
        private String rationale = "Hold trend while rules remain aligned.";
        private List<String> matchedSignals = List.of("sma_20_crosses_above_sma_50");
        private List<StrategyBotAgentToolScope> toolScopes = List.of(
                StrategyBotAgentToolScope.MARKET_CANDLES,
                StrategyBotAgentToolScope.BOT_CONFIG,
                StrategyBotAgentToolScope.RUN_JOURNAL);
        private String providerName = "openai";
        private String providerModel = "gpt-5.4";
        private String promptVersion = "strategy-bot-v1";
        private String providerResponseId = "resp_123";

        private ProposalBuilder(StrategyBotAgentActionType action) {
            this.action = action;
        }

        private ProposalBuilder sizePercent(BigDecimal value) {
            this.sizePercent = value;
            return this;
        }

        private ProposalBuilder closePercent(BigDecimal value) {
            this.closePercent = value;
            return this;
        }

        private ProposalBuilder stopLossPercent(BigDecimal value) {
            this.stopLossPercent = value;
            return this;
        }

        private ProposalBuilder takeProfitPercent(BigDecimal value) {
            this.takeProfitPercent = value;
            return this;
        }

        private StrategyBotAgentActionProposal build() {
            return new StrategyBotAgentActionProposal(
                    action,
                    sizePercent,
                    closePercent,
                    stopLossPercent,
                    takeProfitPercent,
                    rationale,
                    matchedSignals,
                    toolScopes,
                    providerName,
                    providerModel,
                    promptVersion,
                    providerResponseId);
        }
    }
}
