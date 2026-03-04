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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CopyTradingServiceTest {

    @Mock
    private PortfolioParticipantRepository participantRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PortfolioItemRepository portfolioItemRepository;
    @Mock
    private TradeActivityRepository tradeActivityRepository;
    @Mock
    private BinanceService binanceService;

    @InjectMocks
    private CopyTradingService copyTradingService;

    private UUID originalPortfolioId;
    private UUID clonedPortfolioId;
    private Portfolio originalPortfolio;
    private Portfolio clonedPortfolio;
    private PortfolioParticipant participant;

    @BeforeEach
    void setUp() {
        originalPortfolioId = UUID.randomUUID();
        clonedPortfolioId = UUID.randomUUID();

        originalPortfolio = Portfolio.builder()
                .id(originalPortfolioId)
                .name("Master Portfolio")
                .balance(new BigDecimal("50000"))
                .items(new ArrayList<>())
                .build();

        clonedPortfolio = Portfolio.builder()
                .id(clonedPortfolioId)
                .name("Clone Portfolio")
                .balance(new BigDecimal("50000"))
                .items(new ArrayList<>())
                .build();

        participant = PortfolioParticipant.builder()
                .id(UUID.randomUUID())
                .portfolioId(originalPortfolioId)
                .userId(UUID.randomUUID())
                .clonedPortfolioId(clonedPortfolioId)
                .build();
    }

    @Nested
    class ReplicateBuy {

        @Test
        void noParticipants_doesNothing() {
            when(participantRepository.findByPortfolioId(eq(originalPortfolioId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            TradeRequest request = new TradeRequest();
            request.setSymbol("BTCUSDT");
            request.setQuantity(BigDecimal.ONE);

            copyTradingService.replicateBuy(originalPortfolioId, request, BigDecimal.valueOf(50000));

            verify(portfolioRepository, never()).findById(any());
        }

        @Test
        void successfulCopyBuy_createsNewPositionAndDeductsMargin() {
            when(participantRepository.findByPortfolioId(eq(originalPortfolioId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(participant)));
            when(portfolioRepository.findById(originalPortfolioId)).thenReturn(Optional.of(originalPortfolio));
            when(portfolioRepository.findById(clonedPortfolioId)).thenReturn(Optional.of(clonedPortfolio));

            TradeRequest request = new TradeRequest();
            request.setSymbol("BTCUSDT");
            request.setQuantity(new BigDecimal("0.1"));
            request.setLeverage(10);
            request.setSide("LONG");

            BigDecimal price = new BigDecimal("50000");
            copyTradingService.replicateBuy(originalPortfolioId, request, price);

            // Should save the cloned portfolio with reduced balance
            verify(portfolioRepository).save(clonedPortfolio);
            // Should create a new portfolio item
            verify(portfolioItemRepository).save(any(PortfolioItem.class));
            // Should record a trade activity
            ArgumentCaptor<TradeActivity> captor = ArgumentCaptor.forClass(TradeActivity.class);
            verify(tradeActivityRepository).save(captor.capture());
            assertEquals("BUY (COPY)", captor.getValue().getType());
            assertEquals("BTCUSDT", captor.getValue().getSymbol());
        }

        @Test
        void insufficientFunds_skipsCopy() {
            clonedPortfolio.setBalance(BigDecimal.ZERO); // zero balance = impossible to copy

            when(participantRepository.findByPortfolioId(eq(originalPortfolioId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(participant)));
            when(portfolioRepository.findById(originalPortfolioId)).thenReturn(Optional.of(originalPortfolio));
            when(portfolioRepository.findById(clonedPortfolioId)).thenReturn(Optional.of(clonedPortfolio));

            TradeRequest request = new TradeRequest();
            request.setSymbol("BTCUSDT");
            request.setQuantity(new BigDecimal("1"));
            request.setLeverage(1);
            request.setSide("LONG");

            copyTradingService.replicateBuy(originalPortfolioId, request, new BigDecimal("50000"));

            // Should NOT save portfolio item (insufficient funds)
            verify(portfolioItemRepository, never()).save(any());
            verify(tradeActivityRepository, never()).save(any());
        }

        @Test
        void existingPosition_updatesAveragePrice() {
            PortfolioItem existingItem = PortfolioItem.builder()
                    .id(UUID.randomUUID())
                    .symbol("BTCUSDT")
                    .side("LONG")
                    .quantity(new BigDecimal("0.05"))
                    .averagePrice(new BigDecimal("48000"))
                    .leverage(10)
                    .build();
            clonedPortfolio.setItems(new ArrayList<>(List.of(existingItem)));

            when(participantRepository.findByPortfolioId(eq(originalPortfolioId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(participant)));
            when(portfolioRepository.findById(originalPortfolioId)).thenReturn(Optional.of(originalPortfolio));
            when(portfolioRepository.findById(clonedPortfolioId)).thenReturn(Optional.of(clonedPortfolio));

            TradeRequest request = new TradeRequest();
            request.setSymbol("BTCUSDT");
            request.setQuantity(new BigDecimal("0.05"));
            request.setLeverage(10);
            request.setSide("LONG");

            copyTradingService.replicateBuy(originalPortfolioId, request, new BigDecimal("52000"));

            // Should update existing item's average price
            verify(portfolioItemRepository).save(existingItem);
            assertTrue(existingItem.getQuantity().compareTo(new BigDecimal("0.05")) > 0); // quantity increased
        }
    }

    @Nested
    class ReplicateSell {

        @Test
        void noParticipants_doesNothing() {
            when(participantRepository.findByPortfolioId(eq(originalPortfolioId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            TradeRequest request = new TradeRequest();
            request.setSymbol("BTCUSDT");
            request.setQuantity(BigDecimal.ONE);

            copyTradingService.replicateSell(originalPortfolioId, request, BigDecimal.valueOf(50000));

            verify(portfolioRepository, never()).findById(any());
        }

        @Test
        void followerDoesNotHavePosition_skips() {
            when(participantRepository.findByPortfolioId(eq(originalPortfolioId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(participant)));
            when(portfolioRepository.findById(originalPortfolioId)).thenReturn(Optional.of(originalPortfolio));
            when(portfolioRepository.findById(clonedPortfolioId)).thenReturn(Optional.of(clonedPortfolio));

            TradeRequest request = new TradeRequest();
            request.setSymbol("ETHUSDT"); // follower doesn't have ETH
            request.setQuantity(BigDecimal.ONE);

            copyTradingService.replicateSell(originalPortfolioId, request, BigDecimal.valueOf(3000));

            verify(tradeActivityRepository, never()).save(any());
        }

        @Test
        void successfulSell_creditsBalanceAndRecordsTrade() {
            PortfolioItem item = PortfolioItem.builder()
                    .id(UUID.randomUUID())
                    .symbol("BTCUSDT")
                    .side("LONG")
                    .quantity(new BigDecimal("1"))
                    .averagePrice(new BigDecimal("50000"))
                    .leverage(1)
                    .build();
            clonedPortfolio.setItems(new ArrayList<>(List.of(item)));

            when(participantRepository.findByPortfolioId(eq(originalPortfolioId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(participant)));
            when(portfolioRepository.findById(originalPortfolioId)).thenReturn(Optional.of(originalPortfolio));
            when(portfolioRepository.findById(clonedPortfolioId)).thenReturn(Optional.of(clonedPortfolio));

            TradeRequest request = new TradeRequest();
            request.setSymbol("BTCUSDT");
            request.setQuantity(BigDecimal.ONE);

            BigDecimal sellPrice = new BigDecimal("55000");
            copyTradingService.replicateSell(originalPortfolioId, request, sellPrice);

            // Balance should increase (margin + PnL)
            // margin = 50000 * 1 / 1 = 50000, PnL = (55000 - 50000) * 1 = 5000
            // credit = 50000 + 5000 = 55000
            assertEquals(0, new BigDecimal("105000").compareTo(clonedPortfolio.getBalance())); // 50000 + 55000

            // Position should be deleted (full sell)
            verify(portfolioItemRepository).delete(item);

            // Trade activity should be recorded
            ArgumentCaptor<TradeActivity> captor = ArgumentCaptor.forClass(TradeActivity.class);
            verify(tradeActivityRepository).save(captor.capture());
            assertEquals("SELL (COPY)", captor.getValue().getType());
            assertEquals(0, new BigDecimal("5000").compareTo(captor.getValue().getRealizedPnl()));
        }

        @Test
        void partialSell_reducesQuantity() {
            PortfolioItem item = PortfolioItem.builder()
                    .id(UUID.randomUUID())
                    .symbol("BTCUSDT")
                    .side("LONG")
                    .quantity(new BigDecimal("2"))
                    .averagePrice(new BigDecimal("50000"))
                    .leverage(1)
                    .build();
            clonedPortfolio.setItems(new ArrayList<>(List.of(item)));

            when(participantRepository.findByPortfolioId(eq(originalPortfolioId), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(participant)));
            when(portfolioRepository.findById(originalPortfolioId)).thenReturn(Optional.of(originalPortfolio));
            when(portfolioRepository.findById(clonedPortfolioId)).thenReturn(Optional.of(clonedPortfolio));

            TradeRequest request = new TradeRequest();
            request.setSymbol("BTCUSDT");
            request.setQuantity(BigDecimal.ONE); // sell only 1 of 2

            copyTradingService.replicateSell(originalPortfolioId, request, new BigDecimal("50000"));

            // Should not delete, just reduce
            verify(portfolioItemRepository).save(item);
            assertEquals(0, BigDecimal.ONE.compareTo(item.getQuantity()));
        }
    }
}
