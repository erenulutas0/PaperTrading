package com.finance.core.service;

import com.finance.core.dto.StrategyBotAgentActionProposal;
import com.finance.core.dto.StrategyBotAgentDecisionContext;

public interface StrategyBotAgentModelProvider {

    String providerKey();

    StrategyBotAgentActionProposal proposeAction(StrategyBotAgentDecisionContext context);
}
