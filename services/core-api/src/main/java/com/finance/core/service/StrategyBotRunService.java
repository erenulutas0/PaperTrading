package com.finance.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.StrategyBot;
import com.finance.core.domain.StrategyBotRun;
import com.finance.core.dto.StrategyBotRunRequest;
import com.finance.core.dto.StrategyBotRunResponse;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StrategyBotRunService {

    private final StrategyBotRunRepository strategyBotRunRepository;
    private final StrategyBotService strategyBotService;
    private final StrategyBotRuleEngineService strategyBotRuleEngineService;
    private final PortfolioRepository portfolioRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<StrategyBotRunResponse> getRuns(UUID botId, UUID userId, Pageable pageable) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        return strategyBotRunRepository.findByStrategyBotIdAndUserId(bot.getId(), userId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public StrategyBotRunResponse getRun(UUID botId, UUID runId, UUID userId) {
        strategyBotService.getOwnedBotEntity(botId, userId);
        return toResponse(strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Strategy bot run not found")));
    }

    @Transactional
    public StrategyBotRunResponse requestRun(UUID botId, UUID userId, StrategyBotRunRequest request) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        if (bot.getStatus() != StrategyBot.Status.READY) {
            throw new IllegalStateException("Strategy bot must be READY before requesting a run");
        }

        StrategyBotRun.RunMode runMode = resolveRunMode(request != null ? request.getRunMode() : null);
        LocalDate fromDate = request != null ? request.getFromDate() : null;
        LocalDate toDate = request != null ? request.getToDate() : null;
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Run start date must be on or before end date");
        }

        BigDecimal requestedInitialCapital = request != null ? request.getInitialCapital() : null;
        BigDecimal effectiveInitialCapital = resolveInitialCapital(bot, requestedInitialCapital);

        StrategyBotRun run = StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(userId)
                .linkedPortfolioId(bot.getLinkedPortfolioId())
                .runMode(runMode)
                .status(StrategyBotRun.Status.QUEUED)
                .requestedInitialCapital(requestedInitialCapital)
                .effectiveInitialCapital(effectiveInitialCapital)
                .fromDate(fromDate)
                .toDate(toDate)
                .compiledEntryRules(bot.getEntryRules())
                .compiledExitRules(bot.getExitRules())
                .summary(buildQueuedSummary(runMode, bot))
                .build();

        StrategyBotRun saved = strategyBotRunRepository.save(run);
        auditLogService.record(
                userId,
                AuditActionType.STRATEGY_BOT_RUN_REQUESTED,
                AuditResourceType.STRATEGY_BOT_RUN,
                saved.getId(),
                buildAuditDetails(saved, bot));
        return toResponse(saved);
    }

    private BigDecimal resolveInitialCapital(StrategyBot bot, BigDecimal requestedInitialCapital) {
        if (requestedInitialCapital != null) {
            if (requestedInitialCapital.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Initial capital must be positive");
            }
            return requestedInitialCapital;
        }
        if (bot.getLinkedPortfolioId() != null) {
            Portfolio portfolio = portfolioRepository.findById(bot.getLinkedPortfolioId())
                    .orElseThrow(() -> new IllegalArgumentException("Linked portfolio not found"));
            return portfolio.getBalance();
        }
        return new BigDecimal("100000");
    }

    private StrategyBotRun.RunMode resolveRunMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return StrategyBotRun.RunMode.BACKTEST;
        }
        try {
            return StrategyBotRun.RunMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid strategy bot run mode");
        }
    }

    private String buildQueuedSummary(StrategyBotRun.RunMode runMode, StrategyBot bot) {
        StrategyBotRuleEngineService.RuleCompilation compilation = strategyBotRuleEngineService.compile(
                parseJson(bot.getEntryRules()),
                parseJson(bot.getExitRules()),
                bot.getStopLossPercent(),
                bot.getTakeProfitPercent());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("phase", "queued");
        summary.put("runMode", runMode.name());
        summary.put("botStatus", bot.getStatus().name());
        summary.put("market", bot.getMarket());
        summary.put("symbol", bot.getSymbol());
        summary.put("timeframe", bot.getTimeframe());
        summary.put("executionEngineReady", compilation.executionEngineReady());
        summary.put("entryRuleCount", compilation.entryRuleCount());
        summary.put("exitRuleCount", compilation.exitRuleCount());
        summary.put("supportedEntryRuleCount", compilation.supportedEntryRuleCount());
        summary.put("supportedExitRuleCount", compilation.supportedExitRuleCount());
        summary.put("unsupportedRules", compilation.unsupportedRules());
        summary.put("warnings", compilation.warnings());
        summary.put("supportedFeatures", compilation.supportedFeatures());
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize strategy bot run summary", ex);
        }
    }

    private Map<String, Object> buildAuditDetails(StrategyBotRun run, StrategyBot bot) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("strategyBotId", bot.getId());
        details.put("runMode", run.getRunMode().name());
        details.put("status", run.getStatus().name());
        details.put("market", bot.getMarket());
        details.put("symbol", bot.getSymbol());
        details.put("timeframe", bot.getTimeframe());
        details.put("linkedPortfolioId", run.getLinkedPortfolioId());
        details.put("requestedInitialCapital", run.getRequestedInitialCapital());
        details.put("effectiveInitialCapital", run.getEffectiveInitialCapital());
        details.put("fromDate", run.getFromDate());
        details.put("toDate", run.getToDate());
        return details;
    }

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to parse strategy bot run payload", ex);
        }
    }

    private StrategyBotRunResponse toResponse(StrategyBotRun run) {
        return StrategyBotRunResponse.builder()
                .id(run.getId())
                .strategyBotId(run.getStrategyBotId())
                .userId(run.getUserId())
                .linkedPortfolioId(run.getLinkedPortfolioId())
                .runMode(run.getRunMode().name())
                .status(run.getStatus().name())
                .requestedInitialCapital(run.getRequestedInitialCapital())
                .effectiveInitialCapital(run.getEffectiveInitialCapital())
                .fromDate(run.getFromDate())
                .toDate(run.getToDate())
                .compiledEntryRules(parseJson(run.getCompiledEntryRules()))
                .compiledExitRules(parseJson(run.getCompiledExitRules()))
                .summary(parseJson(run.getSummary()))
                .errorMessage(run.getErrorMessage())
                .requestedAt(run.getRequestedAt())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .build();
    }
}
