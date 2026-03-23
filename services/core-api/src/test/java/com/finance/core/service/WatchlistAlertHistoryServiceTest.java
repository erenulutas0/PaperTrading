package com.finance.core.service;

import com.finance.core.domain.WatchlistAlertDirection;
import com.finance.core.domain.WatchlistItem;
import com.finance.core.repository.UserRepository;
import com.finance.core.repository.WatchlistAlertEventRepository;
import com.finance.core.repository.WatchlistItemRepository;
import com.finance.core.web.ApiRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistAlertHistoryServiceTest {

    @Mock
    private WatchlistAlertEventRepository watchlistAlertEventRepository;

    @Mock
    private WatchlistItemRepository watchlistItemRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WatchlistAlertHistoryService watchlistAlertHistoryService;

    private UUID userId;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        itemId = UUID.randomUUID();
    }

    @Test
    void getRecentHistoryPage_requiresExistingUser() {
        when(userRepository.existsById(userId)).thenReturn(false);

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> watchlistAlertHistoryService.getRecentHistoryPage(
                        itemId,
                        userId,
                        PageRequest.of(0, 10),
                        null,
                        WatchlistAlertDirection.ABOVE));

        assertEquals("user_not_found", exception.code());
        assertEquals("User not found", exception.getMessage());
        verifyNoInteractions(watchlistItemRepository);
    }

    @Test
    void exportHistoryCsv_requiresExistingUser() {
        when(userRepository.existsById(userId)).thenReturn(false);

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> watchlistAlertHistoryService.exportHistoryCsv(
                        itemId,
                        userId,
                        null,
                        null));

        assertEquals("user_not_found", exception.code());
        assertEquals("User not found", exception.getMessage());
        verifyNoInteractions(watchlistItemRepository);
    }

    @Test
    void getRecentHistoryPage_requiresOwnedItemWhenUserExists() {
        when(userRepository.existsById(userId)).thenReturn(true);
        when(watchlistItemRepository.findByIdAndWatchlistUserId(itemId, userId)).thenReturn(Optional.empty());

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> watchlistAlertHistoryService.getRecentHistoryPage(
                        itemId,
                        userId,
                        PageRequest.of(0, 10),
                        null,
                        null));

        assertEquals("watchlist_item_not_found", exception.code());
        assertEquals("Watchlist item not found", exception.getMessage());
    }
}
