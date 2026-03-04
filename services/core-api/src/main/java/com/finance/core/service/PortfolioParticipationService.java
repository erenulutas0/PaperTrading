package com.finance.core.service;

import com.finance.core.domain.*;
import com.finance.core.repository.PortfolioItemRepository;
import com.finance.core.repository.PortfolioParticipantRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.core.domain.Notification.NotificationType;
import com.finance.core.domain.event.NotificationEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioParticipationService {

        private final PortfolioRepository portfolioRepository;
        private final PortfolioItemRepository portfolioItemRepository;
        private final PortfolioParticipantRepository participantRepository;
        private final UserRepository userRepository;
        private final ActivityFeedService activityFeedService;
        private final ApplicationEventPublisher eventPublisher;

        /**
         * Join a public portfolio. This creates a clone of the portfolio for the user.
         * The clone starts with the same items/weights as the original.
         */
        @Transactional
        public Map<String, Object> joinPortfolio(UUID portfolioId, UUID userId) {
                Portfolio original = portfolioRepository.findById(portfolioId)
                                .orElseThrow(() -> new RuntimeException("Portfolio not found"));

                if (original.getVisibility() != Portfolio.Visibility.PUBLIC) {
                        throw new RuntimeException("Cannot join a private portfolio");
                }

                if (original.getOwnerId().equals(userId.toString())) {
                        throw new RuntimeException("Cannot join your own portfolio");
                }

                if (participantRepository.existsByPortfolioIdAndUserId(portfolioId, userId)) {
                        throw new RuntimeException("Already joined this portfolio");
                }

                AppUser user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // Create a clone portfolio for the user
                Portfolio clone = Portfolio.builder()
                                .name(original.getName() + " (joined)")
                                .ownerId(userId.toString())
                                .description("Joined from " + original.getName())
                                .visibility(Portfolio.Visibility.PRIVATE)
                                .build();
                clone = portfolioRepository.save(clone);

                // Clone portfolio items
                List<PortfolioItem> originalItems = portfolioItemRepository.findByPortfolioId(original.getId());
                for (PortfolioItem item : originalItems) {
                        PortfolioItem clonedItem = PortfolioItem.builder()
                                        .portfolio(clone)
                                        .symbol(item.getSymbol())
                                        .quantity(item.getQuantity())
                                        .averagePrice(item.getAveragePrice())
                                        .side(item.getSide())
                                        .leverage(item.getLeverage())
                                        .build();
                        portfolioItemRepository.save(clonedItem);
                }

                // Record participation
                PortfolioParticipant participant = PortfolioParticipant.builder()
                                .portfolioId(portfolioId)
                                .userId(userId)
                                .clonedPortfolioId(clone.getId())
                                .build();
                participantRepository.save(participant);

                // Publish activity event
                activityFeedService.publish(
                                userId, user.getUsername(),
                                ActivityEvent.EventType.PORTFOLIO_JOINED,
                                ActivityEvent.TargetType.PORTFOLIO,
                                portfolioId, original.getName());

                // Real-time Notification to portfolio owner
                eventPublisher.publishEvent(NotificationEvent.builder()
                                .receiverId(UUID.fromString(original.getOwnerId()))
                                .actorId(userId)
                                .actorUsername(user.getUsername())
                                .type(NotificationType.PORTFOLIO_JOINED)
                                .referenceId(portfolioId)
                                .referenceLabel(original.getName())
                                .build());

                long count = participantRepository.countByPortfolioId(portfolioId);
                log.info("User {} joined portfolio {} (clone: {}). Total participants: {}",
                                userId, portfolioId, clone.getId(), count);

                return Map.of(
                                "message", "Successfully joined portfolio",
                                "clonedPortfolioId", clone.getId(),
                                "participantCount", count);
        }

        /**
         * Leave a portfolio. Optionally deletes the cloned portfolio.
         */
        @Transactional
        public void leavePortfolio(UUID portfolioId, UUID userId) {
                PortfolioParticipant participant = participantRepository
                                .findByPortfolioIdAndUserId(portfolioId, userId)
                                .orElseThrow(() -> new RuntimeException("Not a participant of this portfolio"));

                AppUser user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
                Portfolio original = portfolioRepository.findById(portfolioId)
                                .orElseThrow(() -> new RuntimeException("Portfolio not found"));

                participantRepository.delete(participant);

                // Publish activity event
                activityFeedService.publish(
                                userId, user.getUsername(),
                                ActivityEvent.EventType.PORTFOLIO_LEFT,
                                ActivityEvent.TargetType.PORTFOLIO,
                                portfolioId, original.getName());

                log.info("User {} left portfolio {}", userId, portfolioId);
        }

        /** Get paginated list of participants for a portfolio */
        public Page<PortfolioParticipant> getParticipants(UUID portfolioId, Pageable pageable) {
                return participantRepository.findByPortfolioId(portfolioId, pageable);
        }

        /** Get participant count for a portfolio */
        public long getParticipantCount(UUID portfolioId) {
                return participantRepository.countByPortfolioId(portfolioId);
        }

        /** Check if user has joined a portfolio */
        public boolean hasJoined(UUID portfolioId, UUID userId) {
                return participantRepository.existsByPortfolioIdAndUserId(portfolioId, userId);
        }
}
