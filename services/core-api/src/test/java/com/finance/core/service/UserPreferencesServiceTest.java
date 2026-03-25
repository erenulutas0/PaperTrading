package com.finance.core.service;

import com.finance.core.domain.UserPreference;
import com.finance.core.dto.UpdateNotificationPreferencesRequest;
import com.finance.core.dto.UpdateTerminalPreferencesRequest;
import com.finance.core.dto.UpdateLeaderboardPreferencesRequest;
import com.finance.core.dto.UserPreferencesResponse;
import com.finance.core.repository.UserPreferenceRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.web.ApiRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserPreferencesService userPreferencesService;

    @Test
    void getPreferences_shouldReturnDefaultsWhenNoRowExists() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPreferenceRepository.findById(userId)).thenReturn(Optional.empty());

        UserPreferencesResponse response = userPreferencesService.getPreferences(userId);

        assertEquals("1D", response.getLeaderboard().getDashboard().getPeriod());
        assertEquals("RETURN_PERCENTAGE", response.getLeaderboard().getDashboard().getSortBy());
        assertEquals("DESC", response.getLeaderboard().getDashboard().getDirection());
        assertEquals("RETURN_PERCENTAGE", response.getLeaderboard().getPublicPage().getSortBy());
        assertEquals("DESC", response.getLeaderboard().getPublicPage().getDirection());
        assertEquals("CRYPTO", response.getTerminal().getMarket());
        assertEquals("BTCUSDT", response.getTerminal().getSymbol());
        assertEquals("1D", response.getTerminal().getRange());
        assertEquals("1h", response.getTerminal().getInterval());
        assertEquals(java.util.List.of(), response.getTerminal().getCompareBaskets());
        assertEquals(java.util.List.of(), response.getTerminal().getScannerViews());
        assertEquals(true, response.getNotification().getInApp().getSocial());
        assertEquals(true, response.getNotification().getInApp().getWatchlist());
        assertEquals(true, response.getNotification().getInApp().getTournaments());
        assertEquals("INSTANT", response.getNotification().getDigestCadence());
        assertEquals(false, response.getNotification().getQuietHours().getEnabled());
        assertEquals("22:00", response.getNotification().getQuietHours().getStart());
        assertEquals("08:00", response.getNotification().getQuietHours().getEnd());
    }

    @Test
    void updateLeaderboardPreferences_shouldNormalizeAndPersist() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPreferenceRepository.findById(userId)).thenReturn(Optional.of(UserPreference.builder()
                .userId(userId)
                .build()));
        when(userPreferenceRepository.save(org.mockito.ArgumentMatchers.any(UserPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateLeaderboardPreferencesRequest request = UpdateLeaderboardPreferencesRequest.builder()
                .dashboard(UpdateLeaderboardPreferencesRequest.DashboardPreferences.builder()
                        .period("1w")
                        .sortBy("roi")
                        .direction("ascending")
                        .build())
                .publicPage(UpdateLeaderboardPreferencesRequest.PublicPreferences.builder()
                        .sortBy("profit")
                        .direction("down")
                        .build())
                .build();

        UserPreferencesResponse response = userPreferencesService.updateLeaderboardPreferences(userId, request);

        assertEquals("1W", response.getLeaderboard().getDashboard().getPeriod());
        assertEquals("RETURN_PERCENTAGE", response.getLeaderboard().getDashboard().getSortBy());
        assertEquals("ASC", response.getLeaderboard().getDashboard().getDirection());
        assertEquals("PROFIT_LOSS", response.getLeaderboard().getPublicPage().getSortBy());
        assertEquals("DESC", response.getLeaderboard().getPublicPage().getDirection());
        verify(userPreferenceRepository).save(org.mockito.ArgumentMatchers.any(UserPreference.class));
    }

    @Test
    void updateTerminalPreferences_shouldNormalizeAndPersist() {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPreferenceRepository.findById(userId)).thenReturn(Optional.of(UserPreference.builder()
                .userId(userId)
                .build()));
        when(userPreferenceRepository.save(org.mockito.ArgumentMatchers.any(UserPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTerminalPreferencesRequest request = UpdateTerminalPreferencesRequest.builder()
                .market("bist100")
                .symbol("thyao")
                .compareSymbols(java.util.List.of("isctr", "garan", "garan"))
                .compareVisible(false)
                .range("6m")
                .interval("4h")
                .favoriteSymbols(java.util.List.of("thyao", "isctr"))
                .compareBaskets(java.util.List.of(
                        UpdateTerminalPreferencesRequest.CompareBasket.builder()
                                .name("Banks")
                                .market("bist100")
                                .symbols(java.util.List.of("isctr", "garan", "isctr"))
                                .updatedAt("2026-03-15T00:00:00Z")
                                .build()))
                .scannerViews(java.util.List.of(
                        UpdateTerminalPreferencesRequest.ScannerView.builder()
                                .name("Bank Winners")
                                .market("bist100")
                                .quickFilter("gainers")
                                .sortMode("alpha")
                                .query("bank")
                                .anchorSymbol("isctr")
                                .updatedAt("2026-03-15T00:00:00Z")
                                .build()))
                .build();

        UserPreferencesResponse response = userPreferencesService.updateTerminalPreferences(userId, request);

        assertEquals("BIST100", response.getTerminal().getMarket());
        assertEquals("THYAO", response.getTerminal().getSymbol());
        assertEquals(java.util.List.of("ISCTR", "GARAN"), response.getTerminal().getCompareSymbols());
        assertEquals(false, response.getTerminal().getCompareVisible());
        assertEquals("6M", response.getTerminal().getRange());
        assertEquals("4h", response.getTerminal().getInterval());
        assertEquals(java.util.List.of("THYAO", "ISCTR"), response.getTerminal().getFavoriteSymbols());
        assertEquals(1, response.getTerminal().getCompareBaskets().size());
        assertEquals("Banks", response.getTerminal().getCompareBaskets().get(0).getName());
        assertEquals("BIST100", response.getTerminal().getCompareBaskets().get(0).getMarket());
        assertEquals(java.util.List.of("ISCTR", "GARAN"), response.getTerminal().getCompareBaskets().get(0).getSymbols());
        assertEquals(1, response.getTerminal().getScannerViews().size());
        assertEquals("Bank Winners", response.getTerminal().getScannerViews().get(0).getName());
        assertEquals("BIST100", response.getTerminal().getScannerViews().get(0).getMarket());
        assertEquals("GAINERS", response.getTerminal().getScannerViews().get(0).getQuickFilter());
        assertEquals("ALPHA", response.getTerminal().getScannerViews().get(0).getSortMode());
        assertEquals("bank", response.getTerminal().getScannerViews().get(0).getQuery());
        assertEquals("ISCTR", response.getTerminal().getScannerViews().get(0).getAnchorSymbol());
        verify(userPreferenceRepository).save(org.mockito.ArgumentMatchers.any(UserPreference.class));
    }

    @Test
    void updateNotificationPreferences_shouldNormalizeAndPersist() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPreferenceRepository.findById(userId)).thenReturn(Optional.of(UserPreference.builder()
                .userId(userId)
                .build()));
        when(userPreferenceRepository.save(org.mockito.ArgumentMatchers.any(UserPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateNotificationPreferencesRequest request = UpdateNotificationPreferencesRequest.builder()
                .inApp(UpdateNotificationPreferencesRequest.InAppPreferences.builder()
                        .social(false)
                        .watchlist(true)
                        .tournaments(false)
                        .build())
                .digestCadence("daily")
                .quietHours(UpdateNotificationPreferencesRequest.QuietHoursPreferences.builder()
                        .enabled(true)
                        .start("21:30")
                        .end("07:45")
                        .build())
                .build();

        UserPreferencesResponse response = userPreferencesService.updateNotificationPreferences(userId, request);

        assertEquals(false, response.getNotification().getInApp().getSocial());
        assertEquals(true, response.getNotification().getInApp().getWatchlist());
        assertEquals(false, response.getNotification().getInApp().getTournaments());
        assertEquals("DAILY", response.getNotification().getDigestCadence());
        assertEquals(true, response.getNotification().getQuietHours().getEnabled());
        assertEquals("21:30", response.getNotification().getQuietHours().getStart());
        assertEquals("07:45", response.getNotification().getQuietHours().getEnd());
        verify(userPreferenceRepository).save(org.mockito.ArgumentMatchers.any(UserPreference.class));
    }

    @Test
    void updateLeaderboardPreferences_withInvalidPeriod_shouldThrow() {
        UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPreferenceRepository.findById(userId)).thenReturn(Optional.of(UserPreference.builder()
                .userId(userId)
                .build()));

        UpdateLeaderboardPreferencesRequest request = UpdateLeaderboardPreferencesRequest.builder()
                .dashboard(UpdateLeaderboardPreferencesRequest.DashboardPreferences.builder()
                        .period("2Y")
                        .build())
                .build();

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> userPreferencesService.updateLeaderboardPreferences(userId, request));

        assertEquals("invalid_user_preferences_period", exception.code());
        assertEquals("Invalid user preferences period", exception.getMessage());
    }

    @Test
    void updateTerminalPreferences_withInvalidRange_shouldThrow() {
        UUID userId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPreferenceRepository.findById(userId)).thenReturn(Optional.of(UserPreference.builder()
                .userId(userId)
                .build()));

        UpdateTerminalPreferencesRequest request = UpdateTerminalPreferencesRequest.builder()
                .range("2Y")
                .build();

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> userPreferencesService.updateTerminalPreferences(userId, request));

        assertEquals("invalid_user_preferences_terminal_range", exception.code());
        assertEquals("Invalid user preferences terminal range", exception.getMessage());
    }

    @Test
    void updateTerminalPreferences_withTooManyCompareBaskets_shouldThrow() {
        UUID userId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPreferenceRepository.findById(userId)).thenReturn(Optional.of(UserPreference.builder()
                .userId(userId)
                .build()));

        UpdateTerminalPreferencesRequest request = UpdateTerminalPreferencesRequest.builder()
                .compareBaskets(IntStream.range(0, 13)
                        .mapToObj(index -> UpdateTerminalPreferencesRequest.CompareBasket.builder()
                                .name("Basket " + index)
                                .market("CRYPTO")
                                .symbols(java.util.List.of("BTCUSDT"))
                                .build())
                        .toList())
                .build();

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> userPreferencesService.updateTerminalPreferences(userId, request));

        assertEquals("user_preferences_compare_basket_limit_reached", exception.code());
        assertEquals("Compare basket limit reached", exception.getMessage());
    }

    @Test
    void updateTerminalPreferences_withInvalidScannerSort_shouldThrow() {
        UUID userId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPreferenceRepository.findById(userId)).thenReturn(Optional.of(UserPreference.builder()
                .userId(userId)
                .build()));

        UpdateTerminalPreferencesRequest request = UpdateTerminalPreferencesRequest.builder()
                .scannerViews(java.util.List.of(
                        UpdateTerminalPreferencesRequest.ScannerView.builder()
                                .name("Bad Sort")
                                .market("CRYPTO")
                                .quickFilter("ALL")
                                .sortMode("SOMETHING_ELSE")
                                .build()))
                .build();

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> userPreferencesService.updateTerminalPreferences(userId, request));

        assertEquals("invalid_user_preferences_scanner_sort", exception.code());
        assertEquals("Invalid user preferences scanner sort", exception.getMessage());
    }

    @Test
    void updateNotificationPreferences_withInvalidDigest_shouldThrow() {
        UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userPreferenceRepository.findById(userId)).thenReturn(Optional.of(UserPreference.builder()
                .userId(userId)
                .build()));

        UpdateNotificationPreferencesRequest request = UpdateNotificationPreferencesRequest.builder()
                .digestCadence("weekly")
                .build();

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> userPreferencesService.updateNotificationPreferences(userId, request));

        assertEquals("invalid_user_preferences_notification_digest", exception.code());
        assertEquals("Invalid user preferences notification digest", exception.getMessage());
    }

    @Test
    void getPreferences_withUnknownUser_shouldThrowTypedNotFound() {
        UUID userId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        when(userRepository.existsById(userId)).thenReturn(false);

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> userPreferencesService.getPreferences(userId));

        assertEquals("user_not_found", exception.code());
        assertEquals("User not found", exception.getMessage());
    }
}
