package com.finance.core.service;

import com.finance.core.domain.MarketTerminalLayout;
import com.finance.core.dto.MarketTerminalLayoutRequest;
import com.finance.core.dto.MarketTerminalLayoutResponse;
import com.finance.core.repository.MarketTerminalLayoutRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.web.ApiRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketTerminalLayoutService {

    private static final String DEFAULT_MARKET = "CRYPTO";
    private static final String DEFAULT_SYMBOL = "BTCUSDT";
    private static final String DEFAULT_RANGE = "1D";
    private static final String DEFAULT_INTERVAL = "1h";
    private static final int MAX_LAYOUTS_PER_USER = 10;
    private static final Set<String> SUPPORTED_MARKETS = Set.of("CRYPTO", "BIST100");
    private static final Set<String> SUPPORTED_RANGES = Set.of("1D", "1W", "1M", "3M", "6M", "1Y", "ALL");
    private static final Set<String> SUPPORTED_INTERVALS = Set.of("1m", "15m", "30m", "1h", "4h", "1d");

    private final MarketTerminalLayoutRepository marketTerminalLayoutRepository;
    private final UserRepository userRepository;

    public List<MarketTerminalLayoutResponse> getLayouts(UUID userId) {
        ensureUserExists(userId);
        return marketTerminalLayoutRepository.findByUserIdOrderByUpdatedAtDescCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Page<MarketTerminalLayoutResponse> getLayouts(UUID userId, Pageable pageable) {
        ensureUserExists(userId);
        return marketTerminalLayoutRepository.findByUserIdOrderByUpdatedAtDescCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public MarketTerminalLayoutResponse createLayout(UUID userId, MarketTerminalLayoutRequest request) {
        lockUserForLayoutMutation(userId);
        if (marketTerminalLayoutRepository.countByUserId(userId) >= MAX_LAYOUTS_PER_USER) {
            throw ApiRequestException.conflict(
                    "market_terminal_layout_limit_reached",
                    "Layout limit reached");
        }

        MarketTerminalLayout layout = marketTerminalLayoutRepository.save(MarketTerminalLayout.builder()
                .userId(userId)
                .name(normalizeName(request.getName()))
                .watchlistId(request.getWatchlistId())
                .market(normalizeMarket(request.getMarket()))
                .symbol(normalizeSymbol(request.getSymbol()))
                .compareSymbols(serializeSymbols(request.getCompareSymbols(), 3))
                .compareVisible(request.getCompareVisible() != null ? request.getCompareVisible() : true)
                .range(normalizeRange(request.getRange()))
                .interval(normalizeInterval(request.getInterval()))
                .favoriteSymbols(serializeSymbols(request.getFavoriteSymbols(), 32))
                .build());
        return toResponse(layout);
    }

    @Transactional
    public MarketTerminalLayoutResponse updateLayout(UUID userId, UUID layoutId, MarketTerminalLayoutRequest request) {
        lockUserForLayoutMutation(userId);
        MarketTerminalLayout layout = marketTerminalLayoutRepository.findByIdAndUserId(layoutId, userId)
                .orElseThrow(() -> ApiRequestException.notFound(
                        "market_terminal_layout_not_found",
                        "Terminal layout not found"));

        layout.setName(normalizeName(request.getName()));
        layout.setWatchlistId(request.getWatchlistId());
        layout.setMarket(normalizeMarket(request.getMarket()));
        layout.setSymbol(normalizeSymbol(request.getSymbol()));
        layout.setCompareSymbols(serializeSymbols(request.getCompareSymbols(), 3));
        layout.setCompareVisible(request.getCompareVisible() != null ? request.getCompareVisible() : true);
        layout.setRange(normalizeRange(request.getRange()));
        layout.setInterval(normalizeInterval(request.getInterval()));
        layout.setFavoriteSymbols(serializeSymbols(request.getFavoriteSymbols(), 32));

        return toResponse(marketTerminalLayoutRepository.save(layout));
    }

    @Transactional
    public void deleteLayout(UUID userId, UUID layoutId) {
        lockUserForLayoutMutation(userId);
        MarketTerminalLayout layout = marketTerminalLayoutRepository.findByIdAndUserId(layoutId, userId)
                .orElseThrow(() -> ApiRequestException.notFound(
                        "market_terminal_layout_not_found",
                        "Terminal layout not found"));
        marketTerminalLayoutRepository.delete(layout);
    }

    private void ensureUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw ApiRequestException.notFound("user_not_found", "User not found");
        }
    }

    private void lockUserForLayoutMutation(UUID userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ApiRequestException.notFound("user_not_found", "User not found"));
    }

    private String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiRequestException.badRequest(
                    "market_terminal_layout_name_required",
                    "Layout name is required");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 80) {
            throw ApiRequestException.badRequest(
                    "market_terminal_layout_name_too_long",
                    "Layout name exceeds 80 characters");
        }
        return trimmed;
    }

    private String normalizeMarket(String raw) {
        if (raw == null) {
            return DEFAULT_MARKET;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_MARKETS.contains(normalized) ? normalized : DEFAULT_MARKET;
    }

    private String normalizeSymbol(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SYMBOL;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRange(String raw) {
        if (raw == null) {
            return DEFAULT_RANGE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_RANGES.contains(normalized) ? normalized : DEFAULT_RANGE;
    }

    private String normalizeInterval(String raw) {
        if (raw == null) {
            return DEFAULT_INTERVAL;
        }
        String trimmed = raw.trim();
        return SUPPORTED_INTERVALS.contains(trimmed) ? trimmed : DEFAULT_INTERVAL;
    }

    private String serializeSymbols(List<String> rawSymbols, int maxSize) {
        if (rawSymbols == null || rawSymbols.isEmpty()) {
            return "";
        }
        return rawSymbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(this::normalizeSymbol)
                .distinct()
                .limit(maxSize)
                .collect(Collectors.joining(","));
    }

    private List<String> deserializeSymbols(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private MarketTerminalLayoutResponse toResponse(MarketTerminalLayout layout) {
        return MarketTerminalLayoutResponse.builder()
                .id(layout.getId())
                .name(layout.getName())
                .watchlistId(layout.getWatchlistId())
                .market(layout.getMarket())
                .symbol(layout.getSymbol())
                .compareSymbols(deserializeSymbols(layout.getCompareSymbols()))
                .compareVisible(layout.getCompareVisible())
                .range(layout.getRange())
                .interval(layout.getInterval())
                .favoriteSymbols(deserializeSymbols(layout.getFavoriteSymbols()))
                .createdAt(layout.getCreatedAt())
                .updatedAt(layout.getUpdatedAt())
                .build();
    }
}
