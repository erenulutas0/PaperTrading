package com.finance.core.service;

import com.finance.core.domain.*;
import com.finance.core.repository.PortfolioItemRepository;
import com.finance.core.repository.PortfolioParticipantRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioParticipationServiceTest {

        @Mock
        private PortfolioRepository portfolioRepository;
        @Mock
        private PortfolioItemRepository portfolioItemRepository;
        @Mock
        private PortfolioParticipantRepository participantRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private ActivityFeedService activityFeedService;
        @Mock
        private org.springframework.context.ApplicationEventPublisher eventPublisher;

        @InjectMocks
        private PortfolioParticipationService service;

        private UUID userId;
        private UUID ownerId;
        private UUID portfolioId;
        private AppUser user;
        private Portfolio portfolio;

        @BeforeEach
        void setUp() {
                userId = UUID.randomUUID();
                ownerId = UUID.randomUUID();
                portfolioId = UUID.randomUUID();

                user = AppUser.builder()
                                .id(userId)
                                .username("joiner")
                                .email("j@test.com")
                                .password("pass")
                                .build();

                portfolio = Portfolio.builder()
                                .id(portfolioId)
                                .name("Growth Portfolio")
                                .ownerId(ownerId.toString())
                                .visibility(Portfolio.Visibility.PUBLIC)
                                .build();
        }

        @Nested
        class JoinPortfolio {

                @Test
                void join_success_clonesPortfolio() {
                        PortfolioItem item = PortfolioItem.builder()
                                        .id(UUID.randomUUID())
                                        .portfolio(portfolio)
                                        .symbol("BTCUSDT")
                                        .quantity(BigDecimal.valueOf(0.5))
                                        .averagePrice(BigDecimal.valueOf(50000))
                                        .side("LONG")
                                        .leverage(1)
                                        .build();

                        Portfolio clone = Portfolio.builder()
                                        .id(UUID.randomUUID())
                                        .name("Growth Portfolio (joined)")
                                        .ownerId(userId.toString())
                                        .build();

                        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
                        when(participantRepository.existsByPortfolioIdAndUserId(portfolioId, userId)).thenReturn(false);
                        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                        when(participantRepository.saveAndFlush(any())).thenAnswer(invocation -> {
                                PortfolioParticipant reserved = invocation.getArgument(0);
                                reserved.setId(UUID.randomUUID());
                                return reserved;
                        });
                        when(portfolioRepository.save(any())).thenReturn(clone);
                        when(portfolioItemRepository.findByPortfolioId(portfolioId)).thenReturn(List.of(item));
                        when(participantRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                        when(participantRepository.countByPortfolioId(portfolioId)).thenReturn(1L);

                        Map<String, Object> result = service.joinPortfolio(portfolioId, userId);

                        assertEquals("Successfully joined portfolio", result.get("message"));
                        assertEquals(1L, result.get("participantCount"));
                        assertNotNull(result.get("clonedPortfolioId"));

                        verify(portfolioRepository).save(any(Portfolio.class));
                        verify(portfolioItemRepository).save(any(PortfolioItem.class));
                        verify(participantRepository).saveAndFlush(any(PortfolioParticipant.class));
                        verify(participantRepository).save(any(PortfolioParticipant.class));
                        verify(activityFeedService).publish(
                                        eq(userId), eq("joiner"),
                                        eq(ActivityEvent.EventType.PORTFOLIO_JOINED),
                                        eq(ActivityEvent.TargetType.PORTFOLIO),
                                        eq(portfolioId), eq("Growth Portfolio"));
                }

                @Test
                void join_privatePortfolio_throws() {
                        portfolio.setVisibility(Portfolio.Visibility.PRIVATE);
                        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

                        RuntimeException ex = assertThrows(RuntimeException.class,
                                        () -> service.joinPortfolio(portfolioId, userId));
                        assertTrue(ex.getMessage().contains("private portfolio"));
                }

                @Test
                void join_ownPortfolio_throws() {
                        portfolio.setOwnerId(userId.toString());
                        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

                        RuntimeException ex = assertThrows(RuntimeException.class,
                                        () -> service.joinPortfolio(portfolioId, userId));
                        assertTrue(ex.getMessage().contains("your own portfolio"));
                }

                @Test
                void join_alreadyJoined_throws() {
                        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
                        when(participantRepository.existsByPortfolioIdAndUserId(portfolioId, userId)).thenReturn(true);

                        RuntimeException ex = assertThrows(RuntimeException.class,
                                        () -> service.joinPortfolio(portfolioId, userId));
                        assertTrue(ex.getMessage().contains("Already joined"));
                }

                @Test
                void join_duplicateConstraintRace_throwsWithoutCloningPortfolio() {
                        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
                        when(participantRepository.existsByPortfolioIdAndUserId(portfolioId, userId)).thenReturn(false);
                        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                        when(participantRepository.saveAndFlush(any()))
                                        .thenThrow(new DataIntegrityViolationException("duplicate key"));

                        RuntimeException ex = assertThrows(RuntimeException.class,
                                        () -> service.joinPortfolio(portfolioId, userId));

                        assertTrue(ex.getMessage().contains("Already joined"));
                        verify(portfolioRepository, never()).save(any(Portfolio.class));
                        verify(portfolioItemRepository, never()).save(any(PortfolioItem.class));
                        verify(participantRepository, never()).save(any(PortfolioParticipant.class));
                }

                @Test
                void join_portfolioNotFound_throws() {
                        when(portfolioRepository.findById(any())).thenReturn(Optional.empty());

                        assertThrows(RuntimeException.class,
                                        () -> service.joinPortfolio(UUID.randomUUID(), userId));
                }
        }

        @Nested
        class LeavePortfolio {

                @Test
                void leave_success() {
                        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
                        when(participantRepository.deleteByPortfolioIdAndUserId(portfolioId, userId)).thenReturn(1);

                        service.leavePortfolio(portfolioId, userId);

                        verify(participantRepository).deleteByPortfolioIdAndUserId(portfolioId, userId);
                        verify(activityFeedService).publish(
                                        eq(userId), eq("joiner"),
                                        eq(ActivityEvent.EventType.PORTFOLIO_LEFT),
                                        eq(ActivityEvent.TargetType.PORTFOLIO),
                                        eq(portfolioId), eq("Growth Portfolio"));
                }

                @Test
                void leave_notParticipant_throws() {
                        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
                        when(participantRepository.deleteByPortfolioIdAndUserId(portfolioId, userId)).thenReturn(0);

                        assertThrows(RuntimeException.class,
                                        () -> service.leavePortfolio(portfolioId, userId));
                }
        }
}
