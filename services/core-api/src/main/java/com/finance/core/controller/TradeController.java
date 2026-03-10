package com.finance.core.controller;

import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.dto.TradeRequest;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.service.BinanceService;
import com.finance.core.web.ApiErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/trade")
@RequiredArgsConstructor
public class TradeController {

    private final PortfolioRepository portfolioRepository;
    private final com.finance.core.repository.PortfolioItemRepository portfolioItemRepository;
    private final com.finance.core.repository.TradeActivityRepository tradeActivityRepository;
    private final BinanceService binanceService;
    private final com.finance.core.service.CopyTradingService copyTradingService;
    private final com.finance.core.service.TournamentService tournamentService;

    @PostMapping("/buy")
    @Transactional
    public ResponseEntity<?> buyAsset(@RequestBody TradeRequest request, HttpServletRequest httpRequest) {
        UUID portfolioId = UUID.fromString(request.getPortfolioId());
        Optional<Portfolio> portfolioOpt = portfolioRepository.findById(portfolioId);

        if (portfolioOpt.isEmpty()) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "portfolio_not_found", "Portfolio not found", null, httpRequest);
        }

        Portfolio portfolio = portfolioOpt.get();
        String symbol = request.getSymbol().toUpperCase();
        Double currentPriceDouble = binanceService.getPrices().get(symbol);

        if (currentPriceDouble == null || currentPriceDouble <= 0) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "price_not_available", "Price not available for symbol: " + symbol, null, httpRequest);
        }

        BigDecimal currentPrice = BigDecimal.valueOf(currentPriceDouble);
        Integer leverage = request.getLeverage() != null && request.getLeverage() > 0 ? request.getLeverage() : 1;
        String side = request.getSide() != null ? request.getSide().toUpperCase() : "LONG";

        // Calculate Notional Value and Required Margin
        BigDecimal notionalValue = currentPrice.multiply(request.getQuantity());
        BigDecimal requiredMargin = notionalValue.divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);

        if (portfolio.getBalance().compareTo(requiredMargin) < 0) {
            return ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    "insufficient_balance",
                    "Insufficient balance for margin. Required: " + requiredMargin + ", Balance: " + portfolio.getBalance(),
                    null,
                    httpRequest);
        }

        // Deduct Margin from Balance
        portfolio.setBalance(portfolio.getBalance().subtract(requiredMargin));
        portfolioRepository.save(portfolio); // Save balance update first

        // updating existing asset or add new
        Optional<PortfolioItem> existingItem = portfolio.getItems().stream()
                .filter(item -> item.getSymbol().equals(symbol) && item.getSide().equals(side))
                .findFirst();

        if (existingItem.isPresent()) {
            PortfolioItem item = existingItem.get();
            // Check leverage match
            Integer existingLev = item.getLeverage() != null ? item.getLeverage() : 1;
            if (!existingLev.equals(leverage)) {
                return ApiErrorResponses.build(
                        HttpStatus.BAD_REQUEST,
                        "leverage_mismatch",
                        "Existing position has leverage " + existingLev + "x. Cannot add with " + leverage + "x.",
                        null,
                        httpRequest);
            }

            BigDecimal oldTotal = item.getQuantity().multiply(item.getAveragePrice());
            BigDecimal newTotal = oldTotal.add(notionalValue);
            BigDecimal newQuantity = item.getQuantity().add(request.getQuantity());

            item.setAveragePrice(newTotal.divide(newQuantity, 8, RoundingMode.HALF_UP));
            item.setQuantity(newQuantity);
            portfolioItemRepository.save(item);
        } else {
            PortfolioItem newItem = PortfolioItem.builder()
                    .portfolio(portfolio)
                    .symbol(symbol)
                    .quantity(request.getQuantity())
                    .averagePrice(currentPrice)
                    .leverage(leverage)
                    .side(side)
                    .build();
            portfolioItemRepository.save(newItem);
            // We need to add to the list so the response is correct if we returned the
            // whole portfolio
            // But since we are saving explicitly, we rely on the repo.
            // However refetching might be safer for response.
            portfolio.getItems().add(newItem);
        }

        com.finance.core.domain.TradeActivity trade = com.finance.core.domain.TradeActivity.builder()
                .portfolioId(portfolioId)
                .symbol(symbol)
                .type("BUY")
                .side(side)
                .quantity(request.getQuantity())
                .price(currentPrice)
                .realizedPnl(BigDecimal.ZERO)
                .build();
        tradeActivityRepository.save(trade);

        // Notify tournament hub if applicable
        tournamentService.notifyTournamentOfTrade(trade);

        // Trigger Copy Trading
        copyTradingService.replicateBuy(portfolioId, request, currentPrice);

        return ResponseEntity.ok(portfolio);
    }

    @PostMapping("/sell")
    @Transactional
    public ResponseEntity<?> sellAsset(@RequestBody TradeRequest request, HttpServletRequest httpRequest) {
        UUID portfolioId = UUID.fromString(request.getPortfolioId());
        Optional<Portfolio> portfolioOpt = portfolioRepository.findById(portfolioId);

        if (portfolioOpt.isEmpty()) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "portfolio_not_found", "Portfolio not found", null, httpRequest);
        }

        Portfolio portfolio = portfolioOpt.get();
        String symbol = request.getSymbol().toUpperCase();
        Double currentPriceDouble = binanceService.getPrices().get(symbol);

        if (currentPriceDouble == null || currentPriceDouble <= 0) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "price_not_available", "Price not available for symbol: " + symbol, null, httpRequest);
        }

        BigDecimal quantity = request.getQuantity();

        // Find the asset
        Optional<PortfolioItem> existingItem = portfolio.getItems().stream()
                .filter(item -> item.getSymbol().equals(symbol))
                .findFirst();

        if (existingItem.isEmpty() || existingItem.get().getQuantity().compareTo(quantity) < 0) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "insufficient_assets", "Insufficient assets to sell.", null, httpRequest);
        }

        PortfolioItem item = existingItem.get();
        BigDecimal currentPrice = BigDecimal.valueOf(currentPriceDouble);
        String side = item.getSide() != null ? item.getSide().toUpperCase() : "LONG";

        // Calculate Payout with Leverage logic
        // Initial Margin for this chunk = (AvgPrice * Qty) / Leverage
        Integer lev = item.getLeverage() != null ? item.getLeverage() : 1;
        BigDecimal initialMargin = item.getAveragePrice().multiply(quantity)
                .divide(BigDecimal.valueOf(lev), 8, RoundingMode.HALF_UP);

        // P/L Calculation:
        // LONG: (CurrentPrice - AvgPrice) * Qty
        // SHORT: (AvgPrice - CurrentPrice) * Qty
        BigDecimal pnl;
        if ("SHORT".equals(side)) {
            pnl = item.getAveragePrice().subtract(currentPrice).multiply(quantity);
        } else {
            pnl = currentPrice.subtract(item.getAveragePrice()).multiply(quantity);
        }

        BigDecimal credit = initialMargin.add(pnl);

        // Add to Balance
        portfolio.setBalance(portfolio.getBalance().add(credit));
        portfolioRepository.save(portfolio);

        // Update Item
        BigDecimal newQuantity = item.getQuantity().subtract(quantity);
        if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            // Remove completely
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

        // Notify tournament hub if applicable
        tournamentService.notifyTournamentOfTrade(trade);

        // Trigger Copy Trading
        copyTradingService.replicateSell(portfolioId, request, currentPrice);

        return ResponseEntity.ok(portfolio);
    }
}
