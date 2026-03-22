package com.finance.core.service;

import com.finance.core.domain.AppUser;
import com.finance.core.domain.MarketTerminalLayout;
import com.finance.core.dto.MarketTerminalLayoutRequest;
import com.finance.core.dto.MarketTerminalLayoutResponse;
import com.finance.core.repository.MarketTerminalLayoutRepository;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketTerminalLayoutServiceTest {

    @Mock
    private MarketTerminalLayoutRepository marketTerminalLayoutRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MarketTerminalLayoutService marketTerminalLayoutService;

    @Test
    void createLayout_locksUserRowBeforeCheckingLimitAndSaving() {
        UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        AppUser user = AppUser.builder()
                .id(userId)
                .username("layout-user")
                .email("layout@test.com")
                .password("pw")
                .build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(marketTerminalLayoutRepository.countByUserId(userId)).thenReturn(2L);
        when(marketTerminalLayoutRepository.save(any(MarketTerminalLayout.class))).thenAnswer(invocation -> {
            MarketTerminalLayout layout = invocation.getArgument(0);
            layout.setId(UUID.fromString("10000000-0000-0000-0000-000000000002"));
            return layout;
        });

        MarketTerminalLayoutResponse response = marketTerminalLayoutService.createLayout(userId, MarketTerminalLayoutRequest.builder()
                .name("  Rail Layout  ")
                .market("bist100")
                .symbol("thyao")
                .compareSymbols(java.util.List.of("garan", "garan", "isctr"))
                .compareVisible(false)
                .range("6m")
                .interval("4h")
                .favoriteSymbols(java.util.List.of("thyao", "isctr"))
                .build());

        ArgumentCaptor<MarketTerminalLayout> layoutCaptor = ArgumentCaptor.forClass(MarketTerminalLayout.class);
        verify(userRepository).findByIdForUpdate(userId);
        verify(marketTerminalLayoutRepository).countByUserId(userId);
        verify(marketTerminalLayoutRepository).save(layoutCaptor.capture());
        MarketTerminalLayout saved = layoutCaptor.getValue();
        assertEquals("Rail Layout", saved.getName());
        assertEquals("BIST100", saved.getMarket());
        assertEquals("THYAO", saved.getSymbol());
        assertEquals("GARAN,ISCTR", saved.getCompareSymbols());
        assertEquals("THYAO,ISCTR", saved.getFavoriteSymbols());
        assertEquals(false, saved.getCompareVisible());
        assertEquals("Rail Layout", response.getName());
        assertEquals("BIST100", response.getMarket());
    }

    @Test
    void createLayout_whenLimitReached_doesNotSave() {
        UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000003");
        AppUser user = AppUser.builder()
                .id(userId)
                .username("layout-user-2")
                .email("layout2@test.com")
                .password("pw")
                .build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(marketTerminalLayoutRepository.countByUserId(userId)).thenReturn(10L);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> marketTerminalLayoutService.createLayout(userId, MarketTerminalLayoutRequest.builder()
                .name("Overflow")
                .build()));

        assertEquals("Layout limit reached", exception.getMessage());
        verify(userRepository).findByIdForUpdate(userId);
        verify(marketTerminalLayoutRepository).countByUserId(userId);
        verify(marketTerminalLayoutRepository, never()).save(any(MarketTerminalLayout.class));
    }

    @Test
    void updateLayout_locksUserRowBeforeResolvingOwnedLayout() {
        UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000004");
        UUID layoutId = UUID.fromString("10000000-0000-0000-0000-000000000005");
        AppUser user = AppUser.builder()
                .id(userId)
                .username("layout-user-3")
                .email("layout3@test.com")
                .password("pw")
                .build();
        MarketTerminalLayout layout = MarketTerminalLayout.builder()
                .id(layoutId)
                .userId(userId)
                .name("Before")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .range("1D")
                .interval("1h")
                .favoriteSymbols("")
                .compareSymbols("")
                .compareVisible(true)
                .build();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(marketTerminalLayoutRepository.findByIdAndUserId(layoutId, userId)).thenReturn(Optional.of(layout));
        when(marketTerminalLayoutRepository.save(any(MarketTerminalLayout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MarketTerminalLayoutResponse response = marketTerminalLayoutService.updateLayout(userId, layoutId, MarketTerminalLayoutRequest.builder()
                .name("After")
                .symbol("ethusdt")
                .build());

        verify(userRepository).findByIdForUpdate(userId);
        verify(marketTerminalLayoutRepository).findByIdAndUserId(layoutId, userId);
        assertEquals("After", response.getName());
        assertEquals("ETHUSDT", response.getSymbol());
    }
}
