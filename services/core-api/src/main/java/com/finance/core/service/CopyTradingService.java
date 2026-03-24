package com.finance.core.service;

import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.PortfolioParticipant;
import com.finance.core.domain.TradeActivity;
import com.finance.core.dto.TradeRequest;
import com.finance.core.repository.PortfolioItemRepository;
import com.finance.core.repository.PortfolioParticipantRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.TradeActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CopyTradingService {

    private final PortfolioParticipantRepository participantRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioItemRepository portfolioItemRepository;
    private final TradeActivityRepository tradeActivityRepository;

    /**
     * Replicates a BUY trade across all cloned portfolios for a given original
     * portfolio.
     */
    public void replicateBuy(UUID originalPortfolioId, TradeRequest request, BigDecimal executedPrice) {
        List<PortfolioParticipant> participants = participantRepository
                .findByPortfolioId(originalPortfolioId, org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        if (participants.isEmpty()) {
            return;
        }

        Portfolio original = portfolioRepository.findById(originalPortfolioId).orElse(null);
        if (original == null)
            return;

        // Calculate dynamic ratio based on follower equity if needed, but for
        // simplicity we keep exact mapping for now
        // A better approach scales the trade quantity relative to the follower's
        // balance vs master's balance.

        for (PortfolioParticipant participant : participants) {
            try {
                executeCopyBuy(participant, original, request, executedPrice);
            } catch (Exception e) {
                log.error("Failed to replicate BUY for cloned portfolio {}: {}", participant.getClonedPortfolioId(),
                        e.getMessage());
            }
        }
    }

    private void executeCopyBuy(PortfolioParticipant participant, Portfolio original, TradeRequest request,
            BigDecimal executedPrice) {
        UUID clonedId = participant.getClonedPortfolioId();
        if (clonedId == null)
            return;

        Portfolio cloned = portfolioRepository.findById(clonedId).orElse(null);
        if (cloned == null) {
            log.warn("Skipping copy BUY because cloned portfolio {} was not found", clonedId);
            return;
        }

        // Determine the trade ratio (Follower Balance / Master Balance prior to trade)
        // Here we assume Master balance before trade is MasterBalance + RequiredMargin.
        // For simplicity of implementation matching exact ratios, we calculate
        // proportional quantity:
        // Follower Qty = Master Qty * (Follower Equity / Master Equity)
        // Since Equity involves prices, we'll simplify: Follower Qty = Master Qty *
        // (Follower Balance / Master Balance (approx))

        // Let's use a 1:1 nominal mirror capped by available balance for standard
        // emulation, or exact ratio.
        // We'll calculate a 'ratio' = cloned.getBalance() / original.getBalance() at
        // the moment of trade.
        // If original balance is 0, ratio = 1 to avoid DivisionByZero.
        BigDecimal originalBalance = original.getBalance().add(request.getQuantity().multiply(executedPrice)); // rough
                                                                                                               // approx
                                                                                                               // of
                                                                                                               // pre-trade
                                                                                                               // balance
        BigDecimal ratio = (originalBalance.compareTo(BigDecimal.ZERO) > 0)
                ? cloned.getBalance().divide(originalBalance, 4, RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        // If ratio is too small, apply minimum or fallback to exact mirror if enough
        // balance
        BigDecimal copyQty = request.getQuantity().multiply(ratio);

        // If for any reason calculation leads to 0, minimum clamp or skip.
        if (copyQty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String symbol = request.getSymbol().toUpperCase(Locale.ROOT);
        Integer leverage = request.getLeverage() != null && request.getLeverage() > 0 ? request.getLeverage() : 1;
        String side = request.getSide() != null ? request.getSide().toUpperCase(Locale.ROOT) : "LONG";

        BigDecimal notionalValue = executedPrice.multiply(copyQty);
        BigDecimal requiredMargin = notionalValue.divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);

        if (cloned.getBalance().compareTo(requiredMargin) < 0) {
            // Can't copy this trade due to insufficient funds
            log.warn("Cloned portfolio {} has insufficient funds to copy trade.", clonedId);
            return;
        }

        // Deduct Margin
        cloned.setBalance(cloned.getBalance().subtract(requiredMargin));
        portfolioRepository.save(cloned);

        // Add Asset
        Optional<PortfolioItem> existingItem = cloned.getItems().stream()
                .filter(item -> item.getSymbol().equals(symbol) && item.getSide().equals(side))
                .findFirst();

        if (existingItem.isPresent()) {
            PortfolioItem item = existingItem.get();
            BigDecimal oldTotal = item.getQuantity().multiply(item.getAveragePrice());
            BigDecimal newTotal = oldTotal.add(notionalValue);
            BigDecimal newQuantity = item.getQuantity().add(copyQty);

            item.setAveragePrice(newTotal.divide(newQuantity, 8, RoundingMode.HALF_UP));
            item.setQuantity(newQuantity);
            portfolioItemRepository.save(item);
        } else {
            PortfolioItem newItem = PortfolioItem.builder()
                    .portfolio(cloned)
                    .symbol(symbol)
                    .quantity(copyQty)
                    .averagePrice(executedPrice)
                    .leverage(leverage)
                    .side(side)
                    .build();
            portfolioItemRepository.save(newItem);
        }

        tradeActivityRepository.save(TradeActivity.builder()
                .portfolioId(clonedId)
                .symbol(symbol)
                .type("BUY (COPY)")
                .side(side)
                .quantity(copyQty)
                .price(executedPrice)
                .build());
    }

    /**
     * Replicates a SELL trade across all cloned portfolios for a given original
     * portfolio.
     */
    public void replicateSell(UUID originalPortfolioId, TradeRequest request, BigDecimal executedPrice) {
        List<PortfolioParticipant> participants = participantRepository
                .findByPortfolioId(originalPortfolioId, org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        if (participants.isEmpty())
            return;

        Portfolio original = portfolioRepository.findById(originalPortfolioId).orElse(null);
        if (original == null)
            return;

        // Find the percentage of the position the master sold
        // To do this properly, we need the master's total quantity before sell, but
        // since we are after the fact,
        // we approximate or we need to pass the sell percentage.
        // Let's assume the user sends exactly what they want to sell.
        // We will just mirror the exact ratio of the portfolio item being sold.

        for (PortfolioParticipant participant : participants) {
            try {
                executeCopySell(participant, original, request, executedPrice);
            } catch (Exception e) {
                log.error("Failed to replicate SELL for cloned portfolio {}: {}", participant.getClonedPortfolioId(),
                        e.getMessage());
            }
        }
    }

    private void executeCopySell(PortfolioParticipant participant, Portfolio original, TradeRequest request,
            BigDecimal executedPrice) {
        UUID clonedId = participant.getClonedPortfolioId();
        if (clonedId == null)
            return;

        Portfolio cloned = portfolioRepository.findById(clonedId).orElse(null);
        if (cloned == null) {
            log.warn("Skipping copy SELL because cloned portfolio {} was not found", clonedId);
            return;
        }
        String symbol = request.getSymbol().toUpperCase(Locale.ROOT);
        String requestedSide = normalizeRequestedSide(request.getSide());

        List<PortfolioItem> matchingItems = cloned.getItems().stream()
                .filter(item -> item.getSymbol().equals(symbol))
                .filter(item -> requestedSide == null || requestedSide.equals(normalizePersistedSide(item.getSide())))
                .toList();

        if (matchingItems.isEmpty()) {
            return; // Follower doesn't have this item
        }
        if (requestedSide == null && matchingItems.size() > 1) {
            log.warn("Skipping copy SELL for cloned portfolio {} because side was ambiguous for symbol {}", clonedId, symbol);
            return;
        }

        PortfolioItem item = matchingItems.get(0);
        String side = normalizePersistedSide(item.getSide());

        // How much of the follower's bag to sell?
        // For simplicity: If master sells 50% of their stack, follower should sell 50%.
        // But since we didn't track master's pre-trade stack, we either pass it or
        // guess.
        // Let's sell the entire position if it matches, or proportional.
        // To do proportional: we need to pass a generic "sellRatio" from
        // TradeController.
        // For now, let's keep it simple: we try to apply the exact Trade ratio, or if
        // follower has less, sell all follower has.
        BigDecimal copyQty = request.getQuantity(); // We'll refine this later by passing the exact percentage from the
                                                    // Trade Controller.

        if (item.getQuantity().compareTo(copyQty) < 0) {
            copyQty = item.getQuantity(); // Sell what they have
        }

        Integer lev = item.getLeverage() != null ? item.getLeverage() : 1;
        BigDecimal initialMargin = item.getAveragePrice().multiply(copyQty)
                .divide(BigDecimal.valueOf(lev), 8, RoundingMode.HALF_UP);

        BigDecimal pnl;
        if ("SHORT".equals(side)) {
            pnl = item.getAveragePrice().subtract(executedPrice).multiply(copyQty);
        } else {
            pnl = executedPrice.subtract(item.getAveragePrice()).multiply(copyQty);
        }

        BigDecimal credit = initialMargin.add(pnl);
        cloned.setBalance(cloned.getBalance().add(credit));
        portfolioRepository.save(cloned);

        BigDecimal newQuantity = item.getQuantity().subtract(copyQty);
        if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            cloned.getItems().remove(item);
            portfolioItemRepository.delete(item);
        } else {
            item.setQuantity(newQuantity);
            portfolioItemRepository.save(item);
        }

        tradeActivityRepository.save(TradeActivity.builder()
                .portfolioId(clonedId)
                .symbol(symbol)
                .type("SELL (COPY)")
                .side(side)
                .quantity(copyQty)
                .price(executedPrice)
                .realizedPnl(pnl)
                .build());
    }

    private String normalizeRequestedSide(String rawSide) {
        if (rawSide == null || rawSide.isBlank()) {
            return null;
        }
        return rawSide.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePersistedSide(String rawSide) {
        if (rawSide == null || rawSide.isBlank()) {
            return "LONG";
        }
        return rawSide.trim().toUpperCase(Locale.ROOT);
    }
}
