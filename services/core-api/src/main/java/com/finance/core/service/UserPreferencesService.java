package com.finance.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.UserPreference;
import com.finance.core.dto.UpdateTerminalPreferencesRequest;
import com.finance.core.dto.UpdateLeaderboardPreferencesRequest;
import com.finance.core.dto.UserPreferencesResponse;
import com.finance.core.repository.UserPreferenceRepository;
import com.finance.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserPreferencesService {

    private static final String DEFAULT_PERIOD = "1D";
    private static final String DEFAULT_SORT_BY = "RETURN_PERCENTAGE";
    private static final String DEFAULT_DIRECTION = "DESC";
    private static final String DEFAULT_TERMINAL_MARKET = "CRYPTO";
    private static final String DEFAULT_TERMINAL_SYMBOL = "BTCUSDT";
    private static final String DEFAULT_TERMINAL_RANGE = "1D";
    private static final String DEFAULT_TERMINAL_INTERVAL = "1h";
    private static final Set<String> SUPPORTED_TERMINAL_MARKETS = Set.of("CRYPTO", "BIST100");
    private static final Set<String> SUPPORTED_PERIODS = Set.of("1D", "1W", "1M", "ALL");
    private static final Set<String> SUPPORTED_SORTS = Set.of("RETURN_PERCENTAGE", "PROFIT_LOSS", "WIN_RATE", "TRUST_SCORE");
    private static final Set<String> SUPPORTED_DIRECTIONS = Set.of("ASC", "DESC");
    private static final Set<String> SUPPORTED_TERMINAL_RANGES = Set.of("1D", "1W", "1M", "3M", "6M", "1Y", "ALL");
    private static final Set<String> SUPPORTED_TERMINAL_INTERVALS = Set.of("1m", "15m", "30m", "1h", "4h", "1d");
    private static final int MAX_COMPARE_BASKETS = 12;
    private static final int MAX_SCANNER_VIEWS = 12;
    private static final Set<String> SUPPORTED_SCANNER_FILTERS = Set.of("ALL", "GAINERS", "LOSERS", "FAVORITES", "SECTOR");
    private static final Set<String> SUPPORTED_SCANNER_SORTS = Set.of("MOVE_DESC", "MOVE_ASC", "PRICE_DESC", "ALPHA");

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserPreferencesResponse getPreferences(UUID userId) {
        ensureUserExists(userId);
        UserPreference preferences = userPreferenceRepository.findById(userId)
                .orElseGet(() -> UserPreference.builder().userId(userId).build());
        return toResponse(preferences);
    }

    @Transactional
    public UserPreferencesResponse updateLeaderboardPreferences(
            UUID userId,
            UpdateLeaderboardPreferencesRequest request) {
        ensureUserExists(userId);
        UserPreference preferences = userPreferenceRepository.findById(userId)
                .orElseGet(() -> UserPreference.builder().userId(userId).build());

        if (request != null && request.getDashboard() != null) {
            UpdateLeaderboardPreferencesRequest.DashboardPreferences dashboard = request.getDashboard();
            if (dashboard.getPeriod() != null) {
                preferences.setDashboardPeriod(parseRequestedPeriod(dashboard.getPeriod()));
            }
            if (dashboard.getSortBy() != null) {
                preferences.setDashboardSortBy(parseRequestedSortBy(dashboard.getSortBy()));
            }
            if (dashboard.getDirection() != null) {
                preferences.setDashboardDirection(parseRequestedDirection(dashboard.getDirection()));
            }
        }

        if (request != null && request.getPublicPage() != null) {
            UpdateLeaderboardPreferencesRequest.PublicPreferences publicPage = request.getPublicPage();
            if (publicPage.getSortBy() != null) {
                preferences.setPublicSortBy(parseRequestedSortBy(publicPage.getSortBy()));
            }
            if (publicPage.getDirection() != null) {
                preferences.setPublicDirection(parseRequestedDirection(publicPage.getDirection()));
            }
        }

        UserPreference saved = userPreferenceRepository.save(preferences);
        log.info("Saved leaderboard preferences for user {}", userId);
        return toResponse(saved);
    }

    @Transactional
    public UserPreferencesResponse updateTerminalPreferences(
            UUID userId,
            UpdateTerminalPreferencesRequest request) {
        ensureUserExists(userId);
        UserPreference preferences = userPreferenceRepository.findById(userId)
                .orElseGet(() -> UserPreference.builder().userId(userId).build());

        if (request != null) {
            if (request.getMarket() != null) {
                preferences.setTerminalMarket(parseRequestedTerminalMarket(request.getMarket()));
            }
            if (request.getSymbol() != null) {
                preferences.setTerminalSymbol(normalizeTerminalSymbol(request.getSymbol()));
            }
            if (request.getCompareSymbols() != null) {
                preferences.setTerminalCompareSymbols(serializeSymbols(request.getCompareSymbols(), 3));
            }
            if (request.getCompareVisible() != null) {
                preferences.setTerminalCompareVisible(request.getCompareVisible());
            }
            if (request.getRange() != null) {
                preferences.setTerminalRange(parseRequestedTerminalRange(request.getRange()));
            }
            if (request.getInterval() != null) {
                preferences.setTerminalInterval(parseRequestedTerminalInterval(request.getInterval()));
            }
            if (request.getFavoriteSymbols() != null) {
                preferences.setTerminalFavoriteSymbols(serializeSymbols(request.getFavoriteSymbols(), 32));
            }
            if (request.getCompareBaskets() != null) {
                preferences.setTerminalCompareBaskets(serializeCompareBaskets(request.getCompareBaskets()));
            }
            if (request.getScannerViews() != null) {
                preferences.setTerminalScannerViews(serializeScannerViews(request.getScannerViews()));
            }
        }

        UserPreference saved = userPreferenceRepository.save(preferences);
        log.info("Saved terminal preferences for user {}", userId);
        return toResponse(saved);
    }

    private UserPreferencesResponse toResponse(UserPreference preferences) {
        return UserPreferencesResponse.builder()
                .leaderboard(UserPreferencesResponse.LeaderboardPreferences.builder()
                        .dashboard(UserPreferencesResponse.DashboardPreferences.builder()
                                .period(normalizePeriod(preferences.getDashboardPeriod()))
                                .sortBy(normalizeSortBy(preferences.getDashboardSortBy()))
                                .direction(normalizeDirection(preferences.getDashboardDirection()))
                                .build())
                        .publicPage(UserPreferencesResponse.PublicPreferences.builder()
                                .sortBy(normalizeSortBy(preferences.getPublicSortBy()))
                                .direction(normalizeDirection(preferences.getPublicDirection()))
                                .build())
                        .build())
                .terminal(UserPreferencesResponse.TerminalPreferences.builder()
                        .market(normalizeTerminalMarket(preferences.getTerminalMarket()))
                        .symbol(normalizeTerminalSymbol(preferences.getTerminalSymbol()))
                        .compareSymbols(deserializeSymbols(preferences.getTerminalCompareSymbols()))
                        .compareVisible(preferences.getTerminalCompareVisible() != null ? preferences.getTerminalCompareVisible() : true)
                        .range(normalizeTerminalRange(preferences.getTerminalRange()))
                        .interval(normalizeTerminalInterval(preferences.getTerminalInterval()))
                        .favoriteSymbols(deserializeSymbols(preferences.getTerminalFavoriteSymbols()))
                        .compareBaskets(deserializeCompareBaskets(preferences.getTerminalCompareBaskets()))
                        .scannerViews(deserializeScannerViews(preferences.getTerminalScannerViews()))
                        .build())
                .build();
    }

    private void ensureUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("User not found");
        }
    }

    private String normalizePeriod(String raw) {
        if (raw == null) {
            return DEFAULT_PERIOD;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_PERIODS.contains(normalized) ? normalized : DEFAULT_PERIOD;
    }

    private String normalizeSortBy(String raw) {
        if (raw == null) {
            return DEFAULT_SORT_BY;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("ROI".equals(normalized) || "RETURN".equals(normalized)) {
            return "RETURN_PERCENTAGE";
        }
        if ("PROFIT".equals(normalized)) {
            return "PROFIT_LOSS";
        }
        if ("WINRATE".equals(normalized) || "WIN".equals(normalized)) {
            return "WIN_RATE";
        }
        if ("TRUST".equals(normalized) || "TRUSTSCORE".equals(normalized)) {
            return "TRUST_SCORE";
        }
        return SUPPORTED_SORTS.contains(normalized) ? normalized : DEFAULT_SORT_BY;
    }

    private String normalizeDirection(String raw) {
        if (raw == null) {
            return DEFAULT_DIRECTION;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("ASCENDING".equals(normalized) || "UP".equals(normalized)) {
            return "ASC";
        }
        if ("DESCENDING".equals(normalized) || "DOWN".equals(normalized)) {
            return "DESC";
        }
        return SUPPORTED_DIRECTIONS.contains(normalized) ? normalized : DEFAULT_DIRECTION;
    }

    private String normalizeTerminalMarket(String raw) {
        if (raw == null) {
            return DEFAULT_TERMINAL_MARKET;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_TERMINAL_MARKETS.contains(normalized) ? normalized : DEFAULT_TERMINAL_MARKET;
    }

    private String normalizeTerminalSymbol(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TERMINAL_SYMBOL;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeTerminalRange(String raw) {
        if (raw == null) {
            return DEFAULT_TERMINAL_RANGE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_TERMINAL_RANGES.contains(normalized) ? normalized : DEFAULT_TERMINAL_RANGE;
    }

    private String normalizeTerminalInterval(String raw) {
        if (raw == null) {
            return DEFAULT_TERMINAL_INTERVAL;
        }
        String trimmed = raw.trim();
        return SUPPORTED_TERMINAL_INTERVALS.contains(trimmed) ? trimmed : DEFAULT_TERMINAL_INTERVAL;
    }

    private String serializeSymbols(List<String> rawSymbols, int maxSize) {
        if (rawSymbols == null || rawSymbols.isEmpty()) {
            return "";
        }
        return rawSymbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(this::normalizeTerminalSymbol)
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

    private String serializeCompareBaskets(List<UpdateTerminalPreferencesRequest.CompareBasket> rawBaskets) {
        if (rawBaskets == null || rawBaskets.isEmpty()) {
            return "";
        }
        if (rawBaskets.size() > MAX_COMPARE_BASKETS) {
            throw new RuntimeException("Compare basket limit reached");
        }
        List<UserPreferencesResponse.CompareBasket> normalized = new ArrayList<>();
        for (UpdateTerminalPreferencesRequest.CompareBasket basket : rawBaskets) {
            if (basket == null) {
                continue;
            }
            List<String> symbols = basket.getSymbols() == null ? List.of() : basket.getSymbols().stream()
                    .filter(symbol -> symbol != null && !symbol.isBlank())
                    .map(this::normalizeTerminalSymbol)
                    .distinct()
                    .limit(3)
                    .toList();
            if (symbols.isEmpty()) {
                continue;
            }
            normalized.add(UserPreferencesResponse.CompareBasket.builder()
                    .name((basket.getName() == null || basket.getName().isBlank()) ? "Compare Basket" : basket.getName().trim())
                    .market(basket.getMarket() == null ? DEFAULT_TERMINAL_MARKET : parseRequestedTerminalMarket(basket.getMarket()))
                    .symbols(symbols)
                    .updatedAt((basket.getUpdatedAt() == null || basket.getUpdatedAt().isBlank()) ? null : basket.getUpdatedAt().trim())
                    .build());
        }
        if (normalized.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception exception) {
            log.warn("Failed to serialize compare baskets: {}", exception.getMessage());
            return "";
        }
    }

    private String serializeScannerViews(List<UpdateTerminalPreferencesRequest.ScannerView> rawViews) {
        if (rawViews == null || rawViews.isEmpty()) {
            return "";
        }
        if (rawViews.size() > MAX_SCANNER_VIEWS) {
            throw new RuntimeException("Scanner view limit reached");
        }
        List<UserPreferencesResponse.ScannerView> normalized = new ArrayList<>();
        for (UpdateTerminalPreferencesRequest.ScannerView view : rawViews) {
            if (view == null) {
                continue;
            }
            normalized.add(UserPreferencesResponse.ScannerView.builder()
                    .name((view.getName() == null || view.getName().isBlank()) ? "Scanner View" : view.getName().trim())
                    .market(view.getMarket() == null ? DEFAULT_TERMINAL_MARKET : parseRequestedTerminalMarket(view.getMarket()))
                    .quickFilter(view.getQuickFilter() == null ? "ALL" : parseRequestedScannerQuickFilter(view.getQuickFilter()))
                    .sortMode(view.getSortMode() == null ? "MOVE_DESC" : parseRequestedScannerSortMode(view.getSortMode()))
                    .query(view.getQuery() == null ? "" : view.getQuery().trim())
                    .anchorSymbol((view.getAnchorSymbol() == null || view.getAnchorSymbol().isBlank()) ? null : normalizeTerminalSymbol(view.getAnchorSymbol()))
                    .updatedAt((view.getUpdatedAt() == null || view.getUpdatedAt().isBlank()) ? null : view.getUpdatedAt().trim())
                    .build());
        }
        if (normalized.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception exception) {
            log.warn("Failed to serialize scanner views: {}", exception.getMessage());
            return "";
        }
    }

    private List<UserPreferencesResponse.CompareBasket> deserializeCompareBaskets(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<UserPreferencesResponse.CompareBasket> parsed = objectMapper.readValue(
                    raw,
                    new TypeReference<List<UserPreferencesResponse.CompareBasket>>() {});
            return parsed.stream()
                    .filter(basket -> basket != null && basket.getSymbols() != null)
                    .map(basket -> UserPreferencesResponse.CompareBasket.builder()
                            .name((basket.getName() == null || basket.getName().isBlank()) ? "Compare Basket" : basket.getName().trim())
                            .market(normalizeTerminalMarket(basket.getMarket()))
                            .symbols(basket.getSymbols().stream()
                                    .filter(symbol -> symbol != null && !symbol.isBlank())
                                    .map(this::normalizeTerminalSymbol)
                                    .distinct()
                                    .limit(3)
                                    .toList())
                            .updatedAt(basket.getUpdatedAt())
                            .build())
                    .filter(basket -> !basket.getSymbols().isEmpty())
                    .limit(MAX_COMPARE_BASKETS)
                    .toList();
        } catch (Exception exception) {
            log.warn("Failed to deserialize compare baskets: {}", exception.getMessage());
            return List.of();
        }
    }

    private List<UserPreferencesResponse.ScannerView> deserializeScannerViews(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<UserPreferencesResponse.ScannerView> parsed = objectMapper.readValue(
                    raw,
                    new TypeReference<List<UserPreferencesResponse.ScannerView>>() {});
            return parsed.stream()
                    .filter(view -> view != null)
                    .map(view -> UserPreferencesResponse.ScannerView.builder()
                            .name((view.getName() == null || view.getName().isBlank()) ? "Scanner View" : view.getName().trim())
                            .market(normalizeTerminalMarket(view.getMarket()))
                            .quickFilter(normalizeScannerQuickFilter(view.getQuickFilter()))
                            .sortMode(normalizeScannerSortMode(view.getSortMode()))
                            .query(view.getQuery() == null ? "" : view.getQuery().trim())
                            .anchorSymbol((view.getAnchorSymbol() == null || view.getAnchorSymbol().isBlank()) ? null : normalizeTerminalSymbol(view.getAnchorSymbol()))
                            .updatedAt(view.getUpdatedAt())
                            .build())
                    .limit(MAX_SCANNER_VIEWS)
                    .toList();
        } catch (Exception exception) {
            log.warn("Failed to deserialize scanner views: {}", exception.getMessage());
            return List.of();
        }
    }

    private String normalizeScannerQuickFilter(String raw) {
        if (raw == null) {
            return "ALL";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_SCANNER_FILTERS.contains(normalized) ? normalized : "ALL";
    }

    private String normalizeScannerSortMode(String raw) {
        if (raw == null) {
            return "MOVE_DESC";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_SCANNER_SORTS.contains(normalized) ? normalized : "MOVE_DESC";
    }

    private String parseRequestedPeriod(String raw) {
        if (raw == null) {
            return DEFAULT_PERIOD;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "1D", "1W", "1M", "ALL" -> normalized;
            default -> throw new RuntimeException("Invalid user preferences period");
        };
    }

    private String parseRequestedSortBy(String raw) {
        if (raw == null) {
            return DEFAULT_SORT_BY;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RETURN_PERCENTAGE", "ROI", "RETURN" -> "RETURN_PERCENTAGE";
            case "PROFIT_LOSS", "PROFIT" -> "PROFIT_LOSS";
            case "WIN_RATE", "WINRATE", "WIN" -> "WIN_RATE";
            case "TRUST_SCORE", "TRUST", "TRUSTSCORE" -> "TRUST_SCORE";
            default -> throw new RuntimeException("Invalid user preferences sort");
        };
    }

    private String parseRequestedDirection(String raw) {
        if (raw == null) {
            return DEFAULT_DIRECTION;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ASC", "ASCENDING", "UP" -> "ASC";
            case "DESC", "DESCENDING", "DOWN" -> "DESC";
            default -> throw new RuntimeException("Invalid user preferences direction");
        };
    }

    private String parseRequestedTerminalMarket(String raw) {
        if (raw == null) {
            return DEFAULT_TERMINAL_MARKET;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TERMINAL_MARKETS.contains(normalized)) {
            throw new RuntimeException("Invalid user preferences terminal market");
        }
        return normalized;
    }

    private String parseRequestedTerminalRange(String raw) {
        if (raw == null) {
            return DEFAULT_TERMINAL_RANGE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TERMINAL_RANGES.contains(normalized)) {
            throw new RuntimeException("Invalid user preferences terminal range");
        }
        return normalized;
    }

    private String parseRequestedTerminalInterval(String raw) {
        if (raw == null) {
            return DEFAULT_TERMINAL_INTERVAL;
        }
        String trimmed = raw.trim();
        if (!SUPPORTED_TERMINAL_INTERVALS.contains(trimmed)) {
            throw new RuntimeException("Invalid user preferences terminal interval");
        }
        return trimmed;
    }

    private String parseRequestedScannerQuickFilter(String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SCANNER_FILTERS.contains(normalized)) {
            throw new RuntimeException("Invalid user preferences scanner filter");
        }
        return normalized;
    }

    private String parseRequestedScannerSortMode(String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SCANNER_SORTS.contains(normalized)) {
            throw new RuntimeException("Invalid user preferences scanner sort");
        }
        return normalized;
    }
}
