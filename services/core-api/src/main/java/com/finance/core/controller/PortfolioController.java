package com.finance.core.controller;

import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.domain.Portfolio;
import com.finance.core.dto.PortfolioRequest;
import com.finance.core.dto.PortfolioResponse;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.service.AuditLogService;
import com.finance.core.service.PerformanceCalculationService;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.PageableRequestParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
@Transactional
public class PortfolioController {

    private final PortfolioRepository portfolioRepository;
    private final com.finance.core.repository.TradeActivityRepository tradeActivityRepository;
    private final com.finance.core.repository.PortfolioSnapshotRepository snapshotRepository;
    private final PerformanceCalculationService performanceCalculationService;
    private final com.finance.core.service.ActivityFeedService feedService;
    private final com.finance.core.repository.UserRepository userRepository;
    private final com.finance.core.service.LeaderboardService leaderboardService;
    private final com.finance.core.service.PerformanceAnalyticsService performanceAnalyticsService;
    private final AuditLogService auditLogService;

    @PostMapping
    public ResponseEntity<?> createPortfolio(@RequestBody PortfolioRequest request, HttpServletRequest httpRequest) {
        validateCreateRequest(request);
        Portfolio.PortfolioBuilder builder = Portfolio.builder()
                .name(request.getName().trim())
                .ownerId(request.getOwnerId().trim());

        // Optional fields
        if (request.getDescription() != null)
            builder.description(request.getDescription());
        if (request.getVisibility() != null) {
            builder.visibility(parseVisibility(request.getVisibility(), false));
        }

        Portfolio saved = portfolioRepository.save(builder.build());

        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("name", saved.getName());
        details.put("visibility", saved.getVisibility().name());
        details.put("description", saved.getDescription());
        auditLogService.record(
                parseActorId(saved.getOwnerId()),
                AuditActionType.PORTFOLIO_CREATED,
                AuditResourceType.PORTFOLIO,
                saved.getId(),
                details);

        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<Page<Portfolio>> listPortfolios(
            @RequestParam String ownerId,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable effectivePageable = PageableRequestParser.resolvePageable(
                pageable,
                page,
                size,
                "invalid_portfolio_page",
                "Invalid portfolio page",
                "invalid_portfolio_size",
                "Invalid portfolio size");
        Page<UUID> idPage = portfolioRepository.findIdsByOwnerId(ownerId, effectivePageable);
        if (idPage.isEmpty()) {
            return ResponseEntity.ok(Page.empty(effectivePageable));
        }
        return ResponseEntity.ok(toOrderedPortfolioPage(idPage, effectivePageable, false));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPortfolio(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return portfolioRepository.findById(id)
                .<ResponseEntity<?>>map(portfolio -> {
                    PortfolioResponse response = mapToResponse(portfolio);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ApiErrorResponses.build(
                        HttpStatus.NOT_FOUND,
                        "portfolio_not_found",
                        "Portfolio not found",
                        null,
                        httpRequest));
    }

    private PortfolioResponse mapToResponse(Portfolio portfolio) {
        BigDecimal currentEquity = performanceCalculationService.calculateCurrentEquity(portfolio);

        List<PortfolioResponse.PortfolioItemDto> items = portfolio.getItems().stream()
                .map(item -> PortfolioResponse.PortfolioItemDto.builder()
                        .id(item.getId())
                        .symbol(item.getSymbol())
                        .quantity(item.getQuantity())
                        .averagePrice(item.getAveragePrice())
                        .leverage(item.getLeverage())
                        .side(item.getSide())
                        .build())
                .collect(Collectors.toList());

        return PortfolioResponse.builder()
                .id(portfolio.getId())
                .name(portfolio.getName())
                .ownerId(portfolio.getOwnerId())
                .description(portfolio.getDescription())
                .visibility(portfolio.getVisibility().name())
                .balance(portfolio.getBalance())
                .totalEquity(currentEquity)
                .returnPercentage1D(performanceCalculationService.calculateReturn(portfolio, "1D"))
                .returnPercentage1W(performanceCalculationService.calculateReturn(portfolio, "1W"))
                .returnPercentage1M(performanceCalculationService.calculateReturn(portfolio, "1M"))
                .returnPercentage1Y(performanceCalculationService.calculateReturn(portfolio, "1Y"))
                .returnPercentageALL(performanceCalculationService.calculateReturn(portfolio, "ALL"))
                .maxDrawdown(performanceAnalyticsService.calculateMaxDrawdown(portfolio.getId()))
                .sharpeRatio(performanceAnalyticsService.calculateSharpeRatio(portfolio.getId()))
                .createdAt(portfolio.getCreatedAt())
                .updatedAt(portfolio.getUpdatedAt())
                .items(items)
                .build();
    }

    // --- Social Discovery ---

    @GetMapping("/discover")
    public ResponseEntity<Page<Portfolio>> discoverPortfolios(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable effectivePageable = PageableRequestParser.resolvePageable(
                pageable,
                page,
                size,
                "invalid_portfolio_page",
                "Invalid portfolio page",
                "invalid_portfolio_size",
                "Invalid portfolio size");
        String normalizedQuery = q == null || q.isBlank() ? "" : q.trim();
        Page<UUID> idPage = portfolioRepository.searchDiscoverableIdsByVisibility(
                Portfolio.Visibility.PUBLIC,
                normalizedQuery,
                effectivePageable);
        if (idPage.isEmpty()) {
            return ResponseEntity.ok(Page.empty(effectivePageable));
        }
        return ResponseEntity.ok(toOrderedPortfolioPage(idPage, effectivePageable, true));
    }

    @PutMapping("/{id}/visibility")
    public ResponseEntity<?> toggleVisibility(
            @PathVariable UUID id,
            @RequestBody VisibilityRequest request,
            HttpServletRequest httpRequest) {
        return portfolioRepository.findById(id).<ResponseEntity<?>>map(portfolio -> {
            Portfolio.Visibility oldVisibility = portfolio.getVisibility();
            Portfolio.Visibility newVisibility = parseVisibility(request != null ? request.getVisibility() : null, true);
            portfolio.setVisibility(newVisibility);
            Portfolio saved = portfolioRepository.save(portfolio);

            // Social logic: If made PUBLIC, publish to feed
            if (oldVisibility == Portfolio.Visibility.PRIVATE && newVisibility == Portfolio.Visibility.PUBLIC) {
                try {
                    UUID authorId = UUID.fromString(portfolio.getOwnerId());
                    String username = userRepository.findById(authorId)
                            .map(com.finance.core.domain.AppUser::getUsername).orElse("User");
                    feedService.publish(authorId, username,
                            com.finance.core.domain.ActivityEvent.EventType.PORTFOLIO_PUBLISHED,
                            com.finance.core.domain.ActivityEvent.TargetType.PORTFOLIO,
                            portfolio.getId(), portfolio.getName());
                } catch (Exception e) {
                    // Log but don't fail transition
                }
            }

            // Refresh leaderboard cache
            leaderboardService.invalidateCache();

            LinkedHashMap<String, Object> details = new LinkedHashMap<>();
            details.put("oldVisibility", oldVisibility.name());
            details.put("newVisibility", newVisibility.name());
            details.put("portfolioName", saved.getName());
            auditLogService.record(
                    parseActorId(saved.getOwnerId()),
                    AuditActionType.PORTFOLIO_VISIBILITY_CHANGED,
                    AuditResourceType.PORTFOLIO,
                    saved.getId(),
                    details);

            return ResponseEntity.ok(saved);
        }).orElseGet(() -> ApiErrorResponses.build(
                HttpStatus.NOT_FOUND,
                "portfolio_not_found",
                "Portfolio not found",
                null,
                httpRequest));
    }

    @lombok.Data
    static class VisibilityRequest {
        private String visibility;
    }

    // --- Deposit ---

    @PostMapping("/{id}/deposit")
    public ResponseEntity<?> deposit(
            @PathVariable UUID id,
            @RequestBody DepositRequest request,
            HttpServletRequest httpRequest) {
        return portfolioRepository.findById(id).map(portfolio -> {
            if (request.getAmount() == null || request.getAmount().signum() <= 0) {
                return ApiErrorResponses.build(
                        HttpStatus.BAD_REQUEST,
                        "invalid_deposit_amount",
                        "Amount must be positive",
                        null,
                        httpRequest);
            }
            portfolio.setBalance(portfolio.getBalance().add(request.getAmount()));
            Portfolio saved = portfolioRepository.save(portfolio);
            LinkedHashMap<String, Object> details = new LinkedHashMap<>();
            details.put("amount", request.getAmount());
            details.put("newBalance", saved.getBalance());
            details.put("portfolioName", saved.getName());
            auditLogService.record(
                    parseActorId(saved.getOwnerId()),
                    AuditActionType.PORTFOLIO_DEPOSITED,
                    AuditResourceType.PORTFOLIO,
                    saved.getId(),
                    details);
            return ResponseEntity.ok(saved);
        }).orElseGet(() -> ApiErrorResponses.build(
                HttpStatus.NOT_FOUND,
                "portfolio_not_found",
                "Portfolio not found",
                null,
                httpRequest));
    }

    @lombok.Data
    static class DepositRequest {
        private java.math.BigDecimal amount;
    }

    private BigDecimal resolveRealizedPnl(com.finance.core.domain.TradeActivity trade) {
        if (trade.getRealizedPnl() != null) {
            return trade.getRealizedPnl();
        }

        String type = trade.getType() != null ? trade.getType().toUpperCase(Locale.ROOT) : "";
        if (type.startsWith("BUY")) {
            return BigDecimal.ZERO;
        }

        return null;
    }

    @lombok.Data
    @lombok.Builder
    static class TradeHistoryEntryResponse {
        private UUID id;
        private String symbol;
        private String type;
        private String side;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal realizedPnl;
        private java.time.LocalDateTime timestamp;
    }

    // --- History & Snapshots ---

    @GetMapping("/{id}/history")
    public ResponseEntity<?> getPortfolioHistory(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String limit,
            HttpServletRequest httpRequest) {
        if (!portfolioRepository.existsById(id)) {
            return ApiErrorResponses.build(
                    HttpStatus.NOT_FOUND,
                    "portfolio_not_found",
                    "Portfolio not found",
                    null,
                    httpRequest);
        }

        Pageable effectivePageable = pageable;
        Integer parsedLimit = parsePortfolioHistoryLimit(limit);
        if (parsedLimit != null) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), parsedLimit, pageable.getSort());
        }

        Page<TradeHistoryEntryResponse> history = tradeActivityRepository.findByPortfolioIdOrderByTimestampDesc(id, effectivePageable)
                .map(trade -> TradeHistoryEntryResponse.builder()
                        .id(trade.getId())
                        .symbol(trade.getSymbol())
                        .type(trade.getType())
                        .side(trade.getSide())
                        .quantity(trade.getQuantity())
                        .price(trade.getPrice())
                        .realizedPnl(resolveRealizedPnl(trade))
                        .timestamp(trade.getTimestamp())
                        .build());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}/snapshots")
    public ResponseEntity<?> getPortfolioSnapshots(@PathVariable UUID id, HttpServletRequest httpRequest) {
        if (!portfolioRepository.existsById(id)) {
            return ApiErrorResponses.build(
                    HttpStatus.NOT_FOUND,
                    "portfolio_not_found",
                    "Portfolio not found",
                    null,
                    httpRequest);
        }
        return ResponseEntity.ok(snapshotRepository.findByPortfolioIdOrderByTimestampAsc(id));
    }

    // --- Delete ---

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePortfolio(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return portfolioRepository.findById(id)
                .<ResponseEntity<?>>map(portfolio -> {
                    portfolioRepository.delete(portfolio);
                    LinkedHashMap<String, Object> details = new LinkedHashMap<>();
                    details.put("portfolioName", portfolio.getName());
                    details.put("visibility", portfolio.getVisibility().name());
                    auditLogService.record(
                            parseActorId(portfolio.getOwnerId()),
                            AuditActionType.PORTFOLIO_DELETED,
                            AuditResourceType.PORTFOLIO,
                            portfolio.getId(),
                            details);
                    return ResponseEntity.ok().build();
                })
                .orElseGet(() -> ApiErrorResponses.build(
                        HttpStatus.NOT_FOUND,
                        "portfolio_not_found",
                        "Portfolio not found",
                        null,
                        httpRequest));
    }

    private UUID parseActorId(String rawOwnerId) {
        if (rawOwnerId == null || rawOwnerId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawOwnerId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Page<Portfolio> toOrderedPortfolioPage(Page<UUID> idPage, Pageable pageable, boolean publicOnly) {
        List<Portfolio> fetched = publicOnly
                ? portfolioRepository.findByIdInAndVisibility(idPage.getContent(), Portfolio.Visibility.PUBLIC)
                : portfolioRepository.findByIdIn(idPage.getContent());
        Map<UUID, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < idPage.getContent().size(); i++) {
            orderIndex.put(idPage.getContent().get(i), i);
        }
        List<Portfolio> ordered = fetched.stream()
                .sorted(Comparator.comparingInt(portfolio -> orderIndex.getOrDefault(portfolio.getId(), Integer.MAX_VALUE)))
                .toList();
        return new PageImpl<>(ordered, pageable, idPage.getTotalElements());
    }

    private void validateCreateRequest(PortfolioRequest request) {
        if (request == null) {
            throw ApiRequestException.badRequest("portfolio_payload_required", "Portfolio payload is required");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw ApiRequestException.badRequest("portfolio_name_required", "Portfolio name is required");
        }
        if (request.getOwnerId() == null || request.getOwnerId().isBlank()) {
            throw ApiRequestException.badRequest("portfolio_owner_required", "Portfolio owner is required");
        }
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            throw ApiRequestException.badRequest(
                    "portfolio_description_too_long",
                    "Portfolio description must be 500 characters or fewer");
        }
    }

    private Portfolio.Visibility parseVisibility(String rawVisibility, boolean required) {
        if (rawVisibility == null || rawVisibility.isBlank()) {
            if (required) {
                throw ApiRequestException.badRequest(
                        "portfolio_visibility_required",
                        "Portfolio visibility is required");
            }
            return null;
        }
        try {
            return Portfolio.Visibility.valueOf(rawVisibility.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiRequestException.badRequest(
                    "invalid_visibility",
                    "Invalid visibility value. Use PUBLIC or PRIVATE.");
        }
    }

    private Integer parsePortfolioHistoryLimit(String rawLimit) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return null;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(rawLimit.trim());
        } catch (NumberFormatException exception) {
            throw ApiRequestException.badRequest(
                    "invalid_portfolio_history_limit",
                    "Portfolio history limit must be an integer between 1 and 100");
        }
        if (parsed < 1 || parsed > 100) {
            throw ApiRequestException.badRequest(
                    "invalid_portfolio_history_limit",
                    "Portfolio history limit must be an integer between 1 and 100");
        }
        return parsed;
    }
}
