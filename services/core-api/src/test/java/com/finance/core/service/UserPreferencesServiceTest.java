package com.finance.core.service;

import com.finance.core.domain.UserPreference;
import com.finance.core.dto.UpdateTerminalPreferencesRequest;
import com.finance.core.dto.UpdateLeaderboardPreferencesRequest;
import com.finance.core.dto.UserPreferencesResponse;
import com.finance.core.repository.UserPreferenceRepository;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        verify(userPreferenceRepository).save(org.mockito.ArgumentMatchers.any(UserPreference.class));
    }
}
