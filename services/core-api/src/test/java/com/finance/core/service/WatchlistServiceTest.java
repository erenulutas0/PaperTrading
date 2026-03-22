package com.finance.core.service;

import com.finance.core.domain.Watchlist;
import com.finance.core.domain.WatchlistItem;
import com.finance.core.dto.MarketInstrumentResponse;
import com.finance.core.repository.UserRepository;
import com.finance.core.repository.WatchlistItemRepository;
import com.finance.core.repository.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock
    private WatchlistRepository watchlistRepository;
    @Mock
    private WatchlistItemRepository watchlistItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MarketDataFacadeService marketDataFacadeService;

    @InjectMocks
    private WatchlistService watchlistService;

    private UUID userId;
    private UUID watchlistId;
    private Watchlist watchlist;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        watchlistId = UUID.randomUUID();
        watchlist = Watchlist.builder()
                .id(watchlistId)
                .userId(userId)
                .name("My Watch")
                .items(new ArrayList<>())
                .build();
    }

    private void stubExistingUser(UUID id) {
        when(userRepository.existsById(id)).thenReturn(true);
    }

    @Nested
    class CreateWatchlist {

        @Test
        void createsWithCustomName() {
            stubExistingUser(userId);
            when(watchlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Watchlist result = watchlistService.createWatchlist(userId, "Crypto Watch");

            assertEquals("Crypto Watch", result.getName());
            assertEquals(userId, result.getUserId());
        }

        @Test
        void createsWithDefaultName_whenNullProvided() {
            stubExistingUser(userId);
            when(watchlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Watchlist result = watchlistService.createWatchlist(userId, null);

            assertEquals("My Watchlist", result.getName());
        }

        @Test
        void throwsWhenUserDoesNotExist() {
            when(userRepository.existsById(userId)).thenReturn(false);

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> watchlistService.createWatchlist(userId, "Crypto Watch"));

            assertEquals("User not found", exception.getMessage());
        }
    }

    @Nested
    class DeleteWatchlist {

        @Test
        void deletesOwnWatchlist() {
            stubExistingUser(userId);
            when(watchlistRepository.findByIdAndUserId(watchlistId, userId)).thenReturn(Optional.of(watchlist));

            watchlistService.deleteWatchlist(watchlistId, userId);

            verify(watchlistRepository).delete(watchlist);
        }

        @Test
        void throwsForNonOwnedWatchlist() {
            UUID otherUserId = UUID.randomUUID();
            stubExistingUser(otherUserId);
            when(watchlistRepository.findByIdAndUserId(watchlistId, otherUserId)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> watchlistService.deleteWatchlist(watchlistId, otherUserId));
        }
    }

    @Nested
    class AddItem {

        @Test
        void addsItemWithAlerts() {
            stubExistingUser(userId);
            when(watchlistRepository.findByIdAndUserId(watchlistId, userId)).thenReturn(Optional.of(watchlist));
            when(watchlistItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WatchlistItem item = watchlistService.addItem(
                    watchlistId, userId,
                    "btcusdt", // should be uppercased
                    new BigDecimal("60000"), // alert above
                    new BigDecimal("40000"), // alert below
                    "Support zone");

            assertEquals("BTCUSDT", item.getSymbol());
            assertEquals(0, new BigDecimal("60000").compareTo(item.getAlertPriceAbove()));
            assertEquals(0, new BigDecimal("40000").compareTo(item.getAlertPriceBelow()));
            assertEquals("Support zone", item.getNotes());
            assertFalse(item.getAlertAboveTriggered());
            assertFalse(item.getAlertBelowTriggered());
        }

        @Test
        void throwsForNonOwnedWatchlist() {
            UUID otherUserId = UUID.randomUUID();
            stubExistingUser(otherUserId);
            when(watchlistRepository.findByIdAndUserId(watchlistId, otherUserId)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class,
                    () -> watchlistService.addItem(watchlistId, otherUserId, "BTCUSDT", null, null, null));
        }
    }

    @Nested
    class UpdateAlerts {

        @Test
        void updatesAlertsAndResetsTriggeredFlags() {
            UUID itemOwnerId = userId;
            stubExistingUser(itemOwnerId);
            WatchlistItem item = WatchlistItem.builder()
                    .id(UUID.randomUUID())
                    .symbol("BTCUSDT")
                    .alertPriceAbove(new BigDecimal("50000"))
                    .alertAboveTriggered(true) // was triggered
                    .alertBelowTriggered(true)
                    .build();

            when(watchlistItemRepository.findByIdAndWatchlistUserId(item.getId(), itemOwnerId)).thenReturn(Optional.of(item));
            when(watchlistItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WatchlistItem updated = watchlistService.updateAlerts(
                    item.getId(),
                    itemOwnerId,
                    new BigDecimal("70000"),
                    new BigDecimal("30000"));

            assertEquals(0, new BigDecimal("70000").compareTo(updated.getAlertPriceAbove()));
            assertEquals(0, new BigDecimal("30000").compareTo(updated.getAlertPriceBelow()));
            // Flags should be reset
            assertFalse(updated.getAlertAboveTriggered());
            assertFalse(updated.getAlertBelowTriggered());
        }

        @Test
        void throwsForNonOwnedItem() {
            UUID otherUserId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            stubExistingUser(otherUserId);
            when(watchlistItemRepository.findByIdAndWatchlistUserId(itemId, otherUserId)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class,
                    () -> watchlistService.updateAlerts(itemId, otherUserId, new BigDecimal("1"), new BigDecimal("2")));
        }
    }

    @Nested
    class RemoveItem {

        @Test
        void removesOwnedItem() {
            UUID itemId = UUID.randomUUID();
            stubExistingUser(userId);
            WatchlistItem item = WatchlistItem.builder()
                    .id(itemId)
                    .symbol("BTCUSDT")
                    .build();
            when(watchlistItemRepository.findByIdAndWatchlistUserId(itemId, userId)).thenReturn(Optional.of(item));

            watchlistService.removeItem(itemId, userId);

            verify(watchlistItemRepository).delete(item);
        }

        @Test
        void throwsForNonOwnedItem() {
            UUID itemId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            stubExistingUser(otherUserId);
            when(watchlistItemRepository.findByIdAndWatchlistUserId(itemId, otherUserId)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> watchlistService.removeItem(itemId, otherUserId));
        }
    }

    @Nested
    class GetEnrichedItems {

        @Test
        void enrichesWithLivePrices() {
            stubExistingUser(userId);
            WatchlistItem item1 = WatchlistItem.builder()
                    .id(UUID.randomUUID()).symbol("BTCUSDT")
                    .alertPriceAbove(new BigDecimal("60000"))
                    .alertAboveTriggered(false)
                    .alertBelowTriggered(false)
                    .build();
            WatchlistItem item2 = WatchlistItem.builder()
                    .id(UUID.randomUUID()).symbol("ETHUSDT")
                    .alertAboveTriggered(false)
                    .alertBelowTriggered(false)
                    .build();

            watchlist.setItems(List.of(item1, item2));

            when(watchlistRepository.findByIdAndUserId(watchlistId, userId)).thenReturn(Optional.of(watchlist));
            when(marketDataFacadeService.getInstrumentSnapshots(List.of("BTCUSDT", "ETHUSDT"))).thenReturn(Map.of(
                    "BTCUSDT", MarketInstrumentResponse.builder().symbol("BTCUSDT").currentPrice(58000.0).changePercent24h(4.25).build(),
                    "ETHUSDT", MarketInstrumentResponse.builder().symbol("ETHUSDT").currentPrice(3200.0).changePercent24h(-1.75).build()));

            List<Map<String, Object>> result = watchlistService.getEnrichedItems(watchlistId, userId);

            assertEquals(2, result.size());
            assertEquals("BTCUSDT", result.get(0).get("symbol"));
            assertEquals(58000.0, result.get(0).get("currentPrice"));
            assertEquals(4.25, result.get(0).get("changePercent24h"));
            assertEquals("ETHUSDT", result.get(1).get("symbol"));
            assertEquals(3200.0, result.get(1).get("currentPrice"));
            assertEquals(-1.75, result.get(1).get("changePercent24h"));
        }
    }
}
