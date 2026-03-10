package com.finance.core.controller;

import com.finance.core.domain.Portfolio;
import com.finance.core.dto.PortfolioRequest;
import com.finance.core.dto.PortfolioResponse;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.service.PerformanceCalculationService;
import com.finance.core.web.ApiErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
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

    @PostMapping
    public ResponseEntity<Portfolio> createPortfolio(@RequestBody PortfolioRequest request) {
        Portfolio.PortfolioBuilder builder = Portfolio.builder()
                .name(request.getName())
                .ownerId(request.getOwnerId());

        // Optional fields
        if (request.getDescription() != null)
            builder.description(request.getDescription());
        if (request.getVisibility() != null) {
            try {
                builder.visibility(Portfolio.Visibility.valueOf(request.getVisibility().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Default to PRIVATE if invalid value
            }
        }

        return ResponseEntity.ok(portfolioRepository.save(builder.build()));
    }

    @GetMapping
    public ResponseEntity<Page<Portfolio>> listPortfolios(
            @RequestParam String ownerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(portfolioRepository.findByOwnerId(ownerId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPortfolio(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return portfolioRepository.findById(id)
                .map(portfolio -> {
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
    public ResponseEntity<Page<Portfolio>> discoverPortfolios(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(portfolioRepository.findByVisibility(Portfolio.Visibility.PUBLIC, pageable));
    }

    @PutMapping("/{id}/visibility")
    public ResponseEntity<?> toggleVisibility(
            @PathVariable UUID id,
            @RequestBody VisibilityRequest request,
            HttpServletRequest httpRequest) {
        return portfolioRepository.findById(id).map(portfolio -> {
            try {
                Portfolio.Visibility oldVisibility = portfolio.getVisibility();
                Portfolio.Visibility newVisibility = Portfolio.Visibility
                        .valueOf(request.getVisibility().toUpperCase());
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

                return ResponseEntity.ok(saved);
            } catch (IllegalArgumentException e) {
                return ApiErrorResponses.build(
                        HttpStatus.BAD_REQUEST,
                        "invalid_visibility",
                        "Invalid visibility value. Use PUBLIC or PRIVATE.",
                        null,
                        httpRequest);
            }
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
            return ResponseEntity.ok(portfolioRepository.save(portfolio));
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

        String type = trade.getType() != null ? trade.getType().toUpperCase() : "";
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
    public ResponseEntity<?> getPortfolioHistory(@PathVariable UUID id) {
        List<TradeHistoryEntryResponse> history = tradeActivityRepository.findByPortfolioIdOrderByTimestampDesc(id)
                .stream()
                .map(trade -> TradeHistoryEntryResponse.builder()
                        .id(trade.getId())
                        .symbol(trade.getSymbol())
                        .type(trade.getType())
                        .side(trade.getSide())
                        .quantity(trade.getQuantity())
                        .price(trade.getPrice())
                        .realizedPnl(resolveRealizedPnl(trade))
                        .timestamp(trade.getTimestamp())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}/snapshots")
    public ResponseEntity<?> getPortfolioSnapshots(@PathVariable UUID id) {
        return ResponseEntity.ok(snapshotRepository.findByPortfolioIdOrderByTimestampAsc(id));
    }

    // --- Delete ---

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePortfolio(@PathVariable UUID id, HttpServletRequest httpRequest) {
        if (!portfolioRepository.existsById(id)) {
            return ApiErrorResponses.build(
                    HttpStatus.NOT_FOUND,
                    "portfolio_not_found",
                    "Portfolio not found",
                    null,
                    httpRequest);
        }
        portfolioRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
