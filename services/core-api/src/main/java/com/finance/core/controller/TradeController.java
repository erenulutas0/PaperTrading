package com.finance.core.controller;

import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.dto.TradeRequest;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.service.AuditLogService;
import com.finance.core.service.BinanceService;
import com.finance.core.web.ApiRequestException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/trade")
@RequiredArgsConstructor
public class TradeController {

    private static final Set<String> SUPPORTED_SIDES = Set.of("LONG", "SHORT");

    private final PortfolioRepository portfolioRepository;
    private final com.finance.core.repository.PortfolioItemRepository portfolioItemRepository;
    private final com.finance.core.repository.TradeActivityRepository tradeActivityRepository;
    private final BinanceService binanceService;
    private final com.finance.core.service.CopyTradingService copyTradingService;
    private final com.finance.core.service.TournamentService tournamentService;
    private final AuditLogService auditLogService;

    @PostMapping("/buy")
    @Transactional
    public ResponseEntity<?> buyAsset(@RequestBody TradeRequest request, HttpServletRequest httpRequest) {
        UUID portfolioId = parseRequiredPortfolioId(request);
        Portfolio portfolio = loadRequiredPortfolio(portfolioId);
        String symbol = normalizeRequiredSymbol(request.getSymbol());
        BigDecimal quantity = requirePositiveQuantity(request.getQuantity());
        BigDecimal currentPrice = resolveCurrentPrice(symbol);
        Integer leverage = resolveBuyLeverage(request.getLeverage());
        String side = resolveTradeSide(request.getSide(), true);

        request.setPortfolioId(portfolioId.toString());
        request.setSymbol(symbol);
        request.setQuantity(quantity);
        request.setLeverage(leverage);
        request.setSide(side);

        BigDecimal notionalValue = currentPrice.multiply(quantity);
        BigDecimal requiredMargin = notionalValue.divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);

        if (portfolio.getBalance().compareTo(requiredMargin) < 0) {
            throw ApiRequestException.badRequest(
                    "insufficient_balance",
                    "Insufficient balance for margin. Required: " + requiredMargin + ", Balance: " + portfolio.getBalance());
        }

        portfolio.setBalance(portfolio.getBalance().subtract(requiredMargin));
        portfolioRepository.save(portfolio);

        Optional<PortfolioItem> existingItem = portfolio.getItems().stream()
                .filter(item -> symbol.equals(item.getSymbol()) && side.equals(normalizePersistedSide(item.getSide())))
                .findFirst();

        if (existingItem.isPresent()) {
            PortfolioItem item = existingItem.get();
            Integer existingLev = item.getLeverage() != null ? item.getLeverage() : 1;
            if (!existingLev.equals(leverage)) {
                throw ApiRequestException.badRequest(
                        "leverage_mismatch",
                        "Existing position has leverage " + existingLev + "x. Cannot add with " + leverage + "x.");
            }

            BigDecimal oldTotal = item.getQuantity().multiply(item.getAveragePrice());
            BigDecimal newTotal = oldTotal.add(notionalValue);
            BigDecimal newQuantity = item.getQuantity().add(quantity);

            item.setAveragePrice(newTotal.divide(newQuantity, 8, RoundingMode.HALF_UP));
            item.setQuantity(newQuantity);
            portfolioItemRepository.save(item);
        } else {
            PortfolioItem newItem = PortfolioItem.builder()
                    .portfolio(portfolio)
                    .symbol(symbol)
                    .quantity(quantity)
                    .averagePrice(currentPrice)
                    .leverage(leverage)
                    .side(side)
                    .build();
            portfolioItemRepository.save(newItem);
            portfolio.getItems().add(newItem);
        }

        com.finance.core.domain.TradeActivity trade = com.finance.core.domain.TradeActivity.builder()
                .portfolioId(portfolioId)
                .symbol(symbol)
                .type("BUY")
                .side(side)
                .quantity(quantity)
                .price(currentPrice)
                .realizedPnl(BigDecimal.ZERO)
                .build();
        tradeActivityRepository.save(trade);

        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("portfolioId", portfolioId);
        details.put("symbol", symbol);
        details.put("side", side);
        details.put("quantity", quantity);
        details.put("price", currentPrice);
        details.put("leverage", leverage);
        details.put("requiredMargin", requiredMargin);
        auditLogService.record(
                parseOwnerId(portfolio.getOwnerId()),
                AuditActionType.TRADE_BUY_EXECUTED,
                AuditResourceType.TRADE,
                trade.getId(),
                details);

        safelyNotifyTournamentTrade(trade);
        safelyReplicateBuy(portfolioId, request, currentPrice);

        return ResponseEntity.ok(portfolio);
    }

    @PostMapping("/sell")
    @Transactional
    public ResponseEntity<?> sellAsset(@RequestBody TradeRequest request, HttpServletRequest httpRequest) {
        UUID portfolioId = parseRequiredPortfolioId(request);
        Portfolio portfolio = loadRequiredPortfolio(portfolioId);
        String symbol = normalizeRequiredSymbol(request.getSymbol());
        BigDecimal quantity = requirePositiveQuantity(request.getQuantity());
        BigDecimal currentPrice = resolveCurrentPrice(symbol);
        String requestedSide = resolveTradeSide(request.getSide(), false);
        PortfolioItem item = resolveSellItem(portfolio, symbol, requestedSide, quantity);
        String side = normalizePersistedSide(item.getSide());

        request.setPortfolioId(portfolioId.toString());
        request.setSymbol(symbol);
        request.setQuantity(quantity);
        request.setSide(side);

        Integer lev = item.getLeverage() != null ? item.getLeverage() : 1;
        BigDecimal initialMargin = item.getAveragePrice().multiply(quantity)
                .divide(BigDecimal.valueOf(lev), 8, RoundingMode.HALF_UP);

        BigDecimal pnl;
        if ("SHORT".equals(side)) {
            pnl = item.getAveragePrice().subtract(currentPrice).multiply(quantity);
        } else {
            pnl = currentPrice.subtract(item.getAveragePrice()).multiply(quantity);
        }

        BigDecimal credit = initialMargin.add(pnl);

        portfolio.setBalance(portfolio.getBalance().add(credit));
        portfolioRepository.save(portfolio);

        BigDecimal newQuantity = item.getQuantity().subtract(quantity);
        if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            portfolio.getItems().remove(item);
            portfolioItemRepository.delete(item);
        } else {
            item.setQuantity(newQuantity);
            portfolioItemRepository.save(item);
        }

        com.finance.core.domain.TradeActivity trade = com.finance.core.domain.TradeActivity.builder()
                .portfolioId(portfolioId)
                .symbol(symbol)
                .type("SELL")
                .side(side)
                .quantity(quantity)
                .price(currentPrice)
                .realizedPnl(pnl)
                .build();
        tradeActivityRepository.save(trade);

        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("portfolioId", portfolioId);
        details.put("symbol", symbol);
        details.put("side", side);
        details.put("quantity", quantity);
        details.put("price", currentPrice);
        details.put("realizedPnl", pnl);
        details.put("credit", credit);
        auditLogService.record(
                parseOwnerId(portfolio.getOwnerId()),
                AuditActionType.TRADE_SELL_EXECUTED,
                AuditResourceType.TRADE,
                trade.getId(),
                details);

        safelyNotifyTournamentTrade(trade);
        safelyReplicateSell(portfolioId, request, currentPrice);

        return ResponseEntity.ok(portfolio);
    }

    private UUID parseRequiredPortfolioId(TradeRequest request) {
        if (request.getPortfolioId() == null || request.getPortfolioId().isBlank()) {
            throw ApiRequestException.badRequest("portfolio_id_invalid", "Portfolio id must be a valid UUID");
        }
        try {
            return UUID.fromString(request.getPortfolioId());
        } catch (IllegalArgumentException ex) {
            throw ApiRequestException.badRequest("portfolio_id_invalid", "Portfolio id must be a valid UUID");
        }
    }

    private Portfolio loadRequiredPortfolio(UUID portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> ApiRequestException.notFound("portfolio_not_found", "Portfolio not found"));
    }

    private String normalizeRequiredSymbol(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw ApiRequestException.badRequest("symbol_required", "Symbol is required");
        }
        return rawSymbol.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal requirePositiveQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw ApiRequestException.badRequest("trade_quantity_invalid", "Trade quantity must be greater than 0");
        }
        return quantity;
    }

    private Integer resolveBuyLeverage(Integer leverage) {
        if (leverage == null) {
            return 1;
        }
        if (leverage <= 0) {
            throw ApiRequestException.badRequest("trade_leverage_invalid", "Trade leverage must be greater than 0");
        }
        return leverage;
    }

    private String resolveTradeSide(String rawSide, boolean defaultLongWhenMissing) {
        if (rawSide == null || rawSide.isBlank()) {
            return defaultLongWhenMissing ? "LONG" : null;
        }
        String normalized = rawSide.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SIDES.contains(normalized)) {
            throw ApiRequestException.badRequest("trade_side_invalid", "Trade side must be LONG or SHORT");
        }
        return normalized;
    }

    private BigDecimal resolveCurrentPrice(String symbol) {
        final Double currentPriceDouble;
        try {
            currentPriceDouble = binanceService.getPrices().get(symbol);
        } catch (RuntimeException ex) {
            log.error("Trade price lookup failed for symbol {}: {}", symbol, ex.getMessage(), ex);
            throw ApiRequestException.internal("trade_price_lookup_failed", "Failed to load trade price");
        }
        if (currentPriceDouble == null || currentPriceDouble <= 0) {
            throw ApiRequestException.badRequest("price_not_available", "Price not available for symbol: " + symbol);
        }
        return BigDecimal.valueOf(currentPriceDouble);
    }

    private PortfolioItem resolveSellItem(
            Portfolio portfolio,
            String symbol,
            String requestedSide,
            BigDecimal quantity) {
        List<PortfolioItem> matchingItems = portfolio.getItems().stream()
                .filter(item -> symbol.equals(item.getSymbol()))
                .filter(item -> requestedSide == null || requestedSide.equals(normalizePersistedSide(item.getSide())))
                .toList();

        if (matchingItems.isEmpty()) {
            throw ApiRequestException.badRequest("insufficient_assets", "Insufficient assets to sell.");
        }
        if (requestedSide == null && matchingItems.size() > 1) {
            throw ApiRequestException.badRequest(
                    "trade_side_required",
                    "Trade side is required when multiple positions exist for symbol");
        }

        PortfolioItem item = matchingItems.get(0);
        if (item.getQuantity().compareTo(quantity) < 0) {
            throw ApiRequestException.badRequest("insufficient_assets", "Insufficient assets to sell.");
        }
        return item;
    }

    private String normalizePersistedSide(String rawSide) {
        if (rawSide == null || rawSide.isBlank()) {
            return "LONG";
        }
        return rawSide.trim().toUpperCase(Locale.ROOT);
    }

    private void safelyNotifyTournamentTrade(com.finance.core.domain.TradeActivity trade) {
        try {
            tournamentService.notifyTournamentOfTrade(trade);
        } catch (RuntimeException ex) {
            log.warn("Tournament trade notification failed for trade {}: {}", trade.getId(), ex.getMessage());
        }
    }

    private void safelyReplicateBuy(UUID portfolioId, TradeRequest request, BigDecimal currentPrice) {
        try {
            copyTradingService.replicateBuy(portfolioId, request, currentPrice);
        } catch (RuntimeException ex) {
            log.warn("Copy BUY replication failed for portfolio {}: {}", portfolioId, ex.getMessage());
        }
    }

    private void safelyReplicateSell(UUID portfolioId, TradeRequest request, BigDecimal currentPrice) {
        try {
            copyTradingService.replicateSell(portfolioId, request, currentPrice);
        } catch (RuntimeException ex) {
            log.warn("Copy SELL replication failed for portfolio {}: {}", portfolioId, ex.getMessage());
        }
    }

    private UUID parseOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(ownerId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
