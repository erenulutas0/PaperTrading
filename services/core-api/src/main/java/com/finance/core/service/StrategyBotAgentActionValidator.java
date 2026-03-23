package com.finance.core.service;

import com.finance.core.dto.StrategyBotAgentActionProposal;
import com.finance.core.dto.StrategyBotAgentActionType;
import com.finance.core.dto.StrategyBotAgentDecisionContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class StrategyBotAgentActionValidator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MAX_RATIONALE_LENGTH = 2000;
    private static final int MAX_MATCHED_SIGNALS = 16;
    private static final int MAX_SIGNAL_LENGTH = 120;
    private static final int MAX_TOOL_SCOPES = 5;

    public void validate(StrategyBotAgentDecisionContext context, StrategyBotAgentActionProposal proposal) {
        require(context != null, "strategy_bot_agent_context_required");
        require(proposal != null, "strategy_bot_agent_proposal_required");
        require(proposal.action() != null, "strategy_bot_agent_action_required");
        requireText(proposal.providerName(), "strategy_bot_agent_provider_required");
        requireText(proposal.providerModel(), "strategy_bot_agent_model_required");
        requireText(proposal.promptVersion(), "strategy_bot_agent_prompt_version_required");
        requireText(proposal.rationale(), "strategy_bot_agent_rationale_required");
        if (proposal.rationale().length() > MAX_RATIONALE_LENGTH) {
            throw new IllegalArgumentException("strategy_bot_agent_rationale_too_long");
        }
        if (proposal.matchedSignals().size() > MAX_MATCHED_SIGNALS) {
            throw new IllegalArgumentException("strategy_bot_agent_too_many_signals");
        }
        for (String signal : proposal.matchedSignals()) {
            requireText(signal, "strategy_bot_agent_signal_invalid");
            if (signal.length() > MAX_SIGNAL_LENGTH) {
                throw new IllegalArgumentException("strategy_bot_agent_signal_invalid");
            }
        }
        if (proposal.toolScopes().isEmpty()) {
            throw new IllegalArgumentException("strategy_bot_agent_tool_scope_required");
        }
        if (proposal.toolScopes().size() > MAX_TOOL_SCOPES) {
            throw new IllegalArgumentException("strategy_bot_agent_tool_scope_too_large");
        }

        switch (proposal.action()) {
            case BUY -> validateBuy(context, proposal);
            case SELL -> validateSell(context, proposal);
            case HOLD -> validateHold(proposal);
            case UPDATE_STOPS -> validateStopUpdate(context, proposal);
        }
    }

    private void validateBuy(StrategyBotAgentDecisionContext context, StrategyBotAgentActionProposal proposal) {
        if (context.positionOpen()) {
            throw new IllegalArgumentException("strategy_bot_agent_buy_requires_flat_position");
        }
        requirePercent(proposal.sizePercent(), "strategy_bot_agent_buy_size_required", "strategy_bot_agent_buy_size_invalid");
        rejectIfPresent(proposal.closePercent(), "strategy_bot_agent_buy_close_percent_forbidden");
        if (proposal.sizePercent().compareTo(cap(context)) > 0) {
            throw new IllegalArgumentException("strategy_bot_agent_buy_size_exceeds_cap");
        }
        validateRiskUpdates(context, proposal, true);
    }

    private void validateSell(StrategyBotAgentDecisionContext context, StrategyBotAgentActionProposal proposal) {
        if (!context.positionOpen()) {
            throw new IllegalArgumentException("strategy_bot_agent_sell_requires_open_position");
        }
        requirePercent(proposal.closePercent(), "strategy_bot_agent_sell_size_required", "strategy_bot_agent_sell_size_invalid");
        rejectIfPresent(proposal.sizePercent(), "strategy_bot_agent_sell_entry_size_forbidden");
        rejectIfPresent(proposal.stopLossPercent(), "strategy_bot_agent_sell_stop_update_forbidden");
        rejectIfPresent(proposal.takeProfitPercent(), "strategy_bot_agent_sell_stop_update_forbidden");
    }

    private void validateHold(StrategyBotAgentActionProposal proposal) {
        rejectIfPresent(proposal.sizePercent(), "strategy_bot_agent_hold_payload_invalid");
        rejectIfPresent(proposal.closePercent(), "strategy_bot_agent_hold_payload_invalid");
        rejectIfPresent(proposal.stopLossPercent(), "strategy_bot_agent_hold_payload_invalid");
        rejectIfPresent(proposal.takeProfitPercent(), "strategy_bot_agent_hold_payload_invalid");
    }

    private void validateStopUpdate(StrategyBotAgentDecisionContext context, StrategyBotAgentActionProposal proposal) {
        if (!context.positionOpen()) {
            throw new IllegalArgumentException("strategy_bot_agent_stop_update_requires_open_position");
        }
        rejectIfPresent(proposal.sizePercent(), "strategy_bot_agent_stop_update_payload_invalid");
        rejectIfPresent(proposal.closePercent(), "strategy_bot_agent_stop_update_payload_invalid");
        if (proposal.stopLossPercent() == null && proposal.takeProfitPercent() == null) {
            throw new IllegalArgumentException("strategy_bot_agent_stop_update_required");
        }
        validateRiskUpdates(context, proposal, false);
    }

    private void validateRiskUpdates(
            StrategyBotAgentDecisionContext context,
            StrategyBotAgentActionProposal proposal,
            boolean allowNoUpdates) {
        if (!allowNoUpdates && proposal.stopLossPercent() == null && proposal.takeProfitPercent() == null) {
            throw new IllegalArgumentException("strategy_bot_agent_stop_update_required");
        }
        if (proposal.stopLossPercent() != null) {
            requireBoundedRisk(
                    proposal.stopLossPercent(),
                    context.configuredStopLossPercent(),
                    "strategy_bot_agent_stop_loss_invalid",
                    "strategy_bot_agent_stop_loss_not_configured",
                    "strategy_bot_agent_stop_loss_exceeds_bot_limit");
        }
        if (proposal.takeProfitPercent() != null) {
            requireBoundedRisk(
                    proposal.takeProfitPercent(),
                    context.configuredTakeProfitPercent(),
                    "strategy_bot_agent_take_profit_invalid",
                    "strategy_bot_agent_take_profit_not_configured",
                    "strategy_bot_agent_take_profit_exceeds_bot_limit");
        }
    }

    private void requireBoundedRisk(
            BigDecimal proposedValue,
            BigDecimal configuredValue,
            String invalidCode,
            String missingConfigCode,
            String exceedsCode) {
        if (proposedValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(invalidCode);
        }
        if (configuredValue == null || configuredValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(missingConfigCode);
        }
        if (proposedValue.compareTo(configuredValue) > 0) {
            throw new IllegalArgumentException(exceedsCode);
        }
    }

    private void requirePercent(BigDecimal value, String missingCode, String invalidCode) {
        if (value == null) {
            throw new IllegalArgumentException(missingCode);
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(invalidCode);
        }
    }

    private void rejectIfPresent(BigDecimal value, String code) {
        if (value != null) {
            throw new IllegalArgumentException(code);
        }
    }

    private void require(boolean condition, String code) {
        if (!condition) {
            throw new IllegalArgumentException(code);
        }
    }

    private void requireText(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(code);
        }
    }

    private BigDecimal cap(StrategyBotAgentDecisionContext context) {
        if (context.maxPositionSizePercent() == null || context.maxPositionSizePercent().compareTo(BigDecimal.ZERO) <= 0) {
            return HUNDRED;
        }
        return context.maxPositionSizePercent().min(HUNDRED);
    }
}
