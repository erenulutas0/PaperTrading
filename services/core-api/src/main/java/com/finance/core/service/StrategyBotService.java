package com.finance.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.StrategyBot;
import com.finance.core.dto.StrategyBotRequest;
import com.finance.core.dto.StrategyBotResponse;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import com.finance.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StrategyBotService {

    private final StrategyBotRepository strategyBotRepository;
    private final StrategyBotRunRepository strategyBotRunRepository;
    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<StrategyBotResponse> getUserBots(UUID userId, Pageable pageable) {
        ensureUserExists(userId);
        return strategyBotRepository.findByUserId(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public StrategyBotResponse getBot(UUID botId, UUID userId) {
        return toResponse(getOwnedBot(botId, userId));
    }

    @Transactional(readOnly = true)
    public StrategyBot getOwnedBotEntity(UUID botId, UUID userId) {
        return getOwnedBot(botId, userId);
    }

    @Transactional
    public StrategyBotResponse createBot(UUID userId, StrategyBotRequest request) {
        ensureUserExists(userId);
        validateCreateRequest(request);
        validateLinkedPortfolio(request.getLinkedPortfolioId(), userId);

        StrategyBot bot = StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(request.getLinkedPortfolioId())
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .market(request.getMarket().trim().toUpperCase())
                .symbol(request.getSymbol().trim().toUpperCase())
                .timeframe(request.getTimeframe().trim().toUpperCase())
                .entryRules(serializeRules(request.getEntryRules()))
                .exitRules(serializeRules(request.getExitRules()))
                .maxPositionSizePercent(request.getMaxPositionSizePercent())
                .stopLossPercent(request.getStopLossPercent())
                .takeProfitPercent(request.getTakeProfitPercent())
                .cooldownMinutes(request.getCooldownMinutes() == null ? 0 : request.getCooldownMinutes())
                .status(resolveStatus(request.getStatus(), StrategyBot.Status.DRAFT))
                .build();

        validateRisk(bot.getMaxPositionSizePercent(), bot.getStopLossPercent(), bot.getTakeProfitPercent(), bot.getCooldownMinutes());

        StrategyBot saved = strategyBotRepository.save(bot);
        auditLogService.record(
                userId,
                AuditActionType.STRATEGY_BOT_CREATED,
                AuditResourceType.STRATEGY_BOT,
                saved.getId(),
                buildAuditDetails(saved));
        return toResponse(saved);
    }

    @Transactional
    public StrategyBotResponse updateBot(UUID botId, UUID userId, StrategyBotRequest request) {
        StrategyBot bot = getOwnedBot(botId, userId);

        if (request == null) {
            throw new IllegalArgumentException("Strategy bot payload is required");
        }
        if (request.getLinkedPortfolioId() != null || bot.getLinkedPortfolioId() != null) {
            UUID resolvedPortfolioId = request.getLinkedPortfolioId() != null ? request.getLinkedPortfolioId() : bot.getLinkedPortfolioId();
            validateLinkedPortfolio(resolvedPortfolioId, userId);
            bot.setLinkedPortfolioId(resolvedPortfolioId);
        }
        if (hasText(request.getName())) {
            bot.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            bot.setDescription(trimToNull(request.getDescription()));
        }
        if (hasText(request.getMarket())) {
            bot.setMarket(request.getMarket().trim().toUpperCase());
        }
        if (hasText(request.getSymbol())) {
            bot.setSymbol(request.getSymbol().trim().toUpperCase());
        }
        if (hasText(request.getTimeframe())) {
            bot.setTimeframe(request.getTimeframe().trim().toUpperCase());
        }
        if (request.getEntryRules() != null) {
            bot.setEntryRules(serializeRules(request.getEntryRules()));
        }
        if (request.getExitRules() != null) {
            bot.setExitRules(serializeRules(request.getExitRules()));
        }
        if (request.getMaxPositionSizePercent() != null) {
            bot.setMaxPositionSizePercent(request.getMaxPositionSizePercent());
        }
        if (request.getStopLossPercent() != null) {
            bot.setStopLossPercent(request.getStopLossPercent());
        }
        if (request.getTakeProfitPercent() != null) {
            bot.setTakeProfitPercent(request.getTakeProfitPercent());
        }
        if (request.getCooldownMinutes() != null) {
            bot.setCooldownMinutes(request.getCooldownMinutes());
        }
        if (request.getStatus() != null) {
            bot.setStatus(resolveStatus(request.getStatus(), bot.getStatus()));
        }

        validateCoreFields(bot);
        validateRisk(bot.getMaxPositionSizePercent(), bot.getStopLossPercent(), bot.getTakeProfitPercent(), bot.getCooldownMinutes());

        StrategyBot saved = strategyBotRepository.save(bot);
        auditLogService.record(
                userId,
                AuditActionType.STRATEGY_BOT_UPDATED,
                AuditResourceType.STRATEGY_BOT,
                saved.getId(),
                buildAuditDetails(saved));
        return toResponse(saved);
    }

    @Transactional
    public void deleteBot(UUID botId, UUID userId) {
        StrategyBot bot = getOwnedBot(botId, userId);
        strategyBotRunRepository.deleteByStrategyBotId(bot.getId());
        strategyBotRepository.delete(bot);
        auditLogService.record(
                userId,
                AuditActionType.STRATEGY_BOT_DELETED,
                AuditResourceType.STRATEGY_BOT,
                bot.getId(),
                buildAuditDetails(bot));
    }

    private StrategyBot getOwnedBot(UUID botId, UUID userId) {
        ensureUserExists(userId);
        return strategyBotRepository.findByIdAndUserId(botId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Strategy bot not found"));
    }

    private void ensureUserExists(UUID userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found");
        }
    }

    private void validateCreateRequest(StrategyBotRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Strategy bot payload is required");
        }
        if (!hasText(request.getName())) {
            throw new IllegalArgumentException("Strategy bot name is required");
        }
        if (!hasText(request.getMarket())) {
            throw new IllegalArgumentException("Strategy bot market is required");
        }
        if (!hasText(request.getSymbol())) {
            throw new IllegalArgumentException("Strategy bot symbol is required");
        }
        if (!hasText(request.getTimeframe())) {
            throw new IllegalArgumentException("Strategy bot timeframe is required");
        }
        if (request.getMaxPositionSizePercent() == null) {
            throw new IllegalArgumentException("Max position size percent is required");
        }
    }

    private void validateCoreFields(StrategyBot bot) {
        if (!hasText(bot.getName())) {
            throw new IllegalArgumentException("Strategy bot name is required");
        }
        if (!hasText(bot.getMarket())) {
            throw new IllegalArgumentException("Strategy bot market is required");
        }
        if (!hasText(bot.getSymbol())) {
            throw new IllegalArgumentException("Strategy bot symbol is required");
        }
        if (!hasText(bot.getTimeframe())) {
            throw new IllegalArgumentException("Strategy bot timeframe is required");
        }
    }

    private void validateLinkedPortfolio(UUID linkedPortfolioId, UUID userId) {
        if (linkedPortfolioId == null) {
            return;
        }
        Portfolio portfolio = portfolioRepository.findById(linkedPortfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Linked portfolio not found"));
        if (!userId.toString().equals(portfolio.getOwnerId())) {
            throw new IllegalArgumentException("Linked portfolio not found");
        }
    }

    private void validateRisk(BigDecimal maxPositionSizePercent,
                              BigDecimal stopLossPercent,
                              BigDecimal takeProfitPercent,
                              Integer cooldownMinutes) {
        if (maxPositionSizePercent == null
                || maxPositionSizePercent.compareTo(BigDecimal.ZERO) <= 0
                || maxPositionSizePercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Max position size percent must be between 0 and 100");
        }
        if (stopLossPercent != null
                && (stopLossPercent.compareTo(BigDecimal.ZERO) <= 0
                || stopLossPercent.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("Stop loss percent must be between 0 and 100");
        }
        if (takeProfitPercent != null && takeProfitPercent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Take profit percent must be positive");
        }
        if (cooldownMinutes == null || cooldownMinutes < 0) {
            throw new IllegalArgumentException("Cooldown minutes must be zero or positive");
        }
    }

    private StrategyBot.Status resolveStatus(String raw, StrategyBot.Status fallback) {
        if (!hasText(raw)) {
            return fallback;
        }
        try {
            return StrategyBot.Status.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid strategy bot status");
        }
    }

    private String serializeRules(JsonNode rules) {
        JsonNode normalized = rules == null ? objectMapper.createObjectNode() : rules;
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize strategy rules", ex);
        }
    }

    private JsonNode deserializeRules(String raw) {
        try {
            return objectMapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to parse stored strategy rules", ex);
        }
    }

    private StrategyBotResponse toResponse(StrategyBot bot) {
        return StrategyBotResponse.builder()
                .id(bot.getId())
                .userId(bot.getUserId())
                .linkedPortfolioId(bot.getLinkedPortfolioId())
                .name(bot.getName())
                .description(bot.getDescription())
                .botKind(bot.getBotKind().name())
                .status(bot.getStatus().name())
                .market(bot.getMarket())
                .symbol(bot.getSymbol())
                .timeframe(bot.getTimeframe())
                .entryRules(deserializeRules(bot.getEntryRules()))
                .exitRules(deserializeRules(bot.getExitRules()))
                .maxPositionSizePercent(bot.getMaxPositionSizePercent())
                .stopLossPercent(bot.getStopLossPercent())
                .takeProfitPercent(bot.getTakeProfitPercent())
                .cooldownMinutes(bot.getCooldownMinutes())
                .createdAt(bot.getCreatedAt())
                .updatedAt(bot.getUpdatedAt())
                .build();
    }

    private Map<String, Object> buildAuditDetails(StrategyBot bot) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("name", bot.getName());
        details.put("status", bot.getStatus().name());
        details.put("market", bot.getMarket());
        details.put("symbol", bot.getSymbol());
        details.put("timeframe", bot.getTimeframe());
        details.put("linkedPortfolioId", bot.getLinkedPortfolioId());
        return details;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
