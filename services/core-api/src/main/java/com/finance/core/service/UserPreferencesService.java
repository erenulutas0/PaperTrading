package com.finance.core.service;

import com.finance.core.domain.UserPreference;
import com.finance.core.dto.UpdateLeaderboardPreferencesRequest;
import com.finance.core.dto.UserPreferencesResponse;
import com.finance.core.repository.UserPreferenceRepository;
import com.finance.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserPreferencesService {

    private static final String DEFAULT_PERIOD = "1D";
    private static final String DEFAULT_SORT_BY = "RETURN_PERCENTAGE";
    private static final String DEFAULT_DIRECTION = "DESC";
    private static final Set<String> SUPPORTED_PERIODS = Set.of("1D", "1W", "1M", "ALL");
    private static final Set<String> SUPPORTED_SORTS = Set.of("RETURN_PERCENTAGE", "PROFIT_LOSS", "WIN_RATE", "TRUST_SCORE");
    private static final Set<String> SUPPORTED_DIRECTIONS = Set.of("ASC", "DESC");

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;

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
                preferences.setDashboardPeriod(normalizePeriod(dashboard.getPeriod()));
            }
            if (dashboard.getSortBy() != null) {
                preferences.setDashboardSortBy(normalizeSortBy(dashboard.getSortBy()));
            }
            if (dashboard.getDirection() != null) {
                preferences.setDashboardDirection(normalizeDirection(dashboard.getDirection()));
            }
        }

        if (request != null && request.getPublicPage() != null) {
            UpdateLeaderboardPreferencesRequest.PublicPreferences publicPage = request.getPublicPage();
            if (publicPage.getSortBy() != null) {
                preferences.setPublicSortBy(normalizeSortBy(publicPage.getSortBy()));
            }
            if (publicPage.getDirection() != null) {
                preferences.setPublicDirection(normalizeDirection(publicPage.getDirection()));
            }
        }

        UserPreference saved = userPreferenceRepository.save(preferences);
        log.info("Saved leaderboard preferences for user {}", userId);
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
}
