package com.finance.core.service;

import com.finance.core.domain.AppUser;
import com.finance.core.domain.Interaction;
import com.finance.core.domain.Portfolio;
import com.finance.core.dto.InteractionRequest;
import com.finance.core.repository.AnalysisPostRepository;
import com.finance.core.repository.InteractionRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InteractionServiceTest {

    @Mock
    private InteractionRepository interactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private AnalysisPostRepository analysisPostRepository;
    @Mock
    private ActivityFeedService activityFeedService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private InteractionService interactionService;

    private UUID actorId;
    private UUID targetId;
    private InteractionRequest likeRequest;
    private InteractionRequest commentRequest;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        targetId = UUID.randomUUID();

        likeRequest = new InteractionRequest();
        likeRequest.setTargetType("PORTFOLIO");

        commentRequest = new InteractionRequest();
        commentRequest.setTargetType("PORTFOLIO");
        commentRequest.setContent("Great portfolio!");
    }

    @Test
    void toggleLike_WhenNotLiked_ShouldCreateLikeAndNotify() {
        // Arrange
        when(interactionRepository.findByActorIdAndTargetTypeAndTargetIdAndInteractionType(
                actorId, Interaction.TargetType.PORTFOLIO, targetId, Interaction.InteractionType.LIKE))
                .thenReturn(Optional.empty());

        when(userRepository.findById(actorId)).thenReturn(Optional.of(new AppUser()));
        Portfolio portfolio = new Portfolio();
        portfolio.setOwnerId(UUID.randomUUID().toString());
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio));

        // Act
        interactionService.toggleLike(actorId, targetId, likeRequest);

        // Assert
        verify(interactionRepository, times(1)).save(any(Interaction.class));
        verify(eventPublisher, times(1)).publishEvent(any(Object.class)); // Notification triggered
        verify(activityFeedService, times(1)).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void toggleLike_WhenConcurrentDuplicateLike_ShouldBeIdempotent() {
        when(interactionRepository.findByActorIdAndTargetTypeAndTargetIdAndInteractionType(
                actorId, Interaction.TargetType.PORTFOLIO, targetId, Interaction.InteractionType.LIKE))
                .thenReturn(Optional.empty());

        when(userRepository.findById(actorId)).thenReturn(Optional.of(new AppUser()));
        Portfolio portfolio = new Portfolio();
        portfolio.setOwnerId(UUID.randomUUID().toString());
        portfolio.setName("Race Portfolio");
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio));
        when(interactionRepository.save(any(Interaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        interactionService.toggleLike(actorId, targetId, likeRequest);

        verify(eventPublisher, never()).publishEvent(any());
        verify(activityFeedService, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void toggleLike_WhenAlreadyLiked_ShouldDeleteLike() {
        // Arrange
        Interaction existingLike = new Interaction();
        Portfolio portfolio = new Portfolio();
        portfolio.setOwnerId(UUID.randomUUID().toString());
        when(interactionRepository.findByActorIdAndTargetTypeAndTargetIdAndInteractionType(
                actorId, Interaction.TargetType.PORTFOLIO, targetId, Interaction.InteractionType.LIKE))
                .thenReturn(Optional.of(existingLike));
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio));

        // Act
        interactionService.toggleLike(actorId, targetId, likeRequest);

        // Assert
        verify(interactionRepository, times(1)).delete(existingLike);
        verify(interactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class)); // No notification on unlike
    }

    @Test
    void addComment_WithValidContent_ShouldSaveComment() {
        // Arrange
        when(userRepository.findById(actorId)).thenReturn(Optional.of(new AppUser()));
        Portfolio portfolio = new Portfolio();
        portfolio.setOwnerId(UUID.randomUUID().toString());
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio));

        Interaction savedComment = Interaction.builder()
                .content("Great portfolio!")
                .build();
        when(interactionRepository.save(any())).thenReturn(savedComment);

        // Act
        Interaction result = interactionService.addComment(actorId, targetId, commentRequest);

        // Assert
        assertNotNull(result);
        assertEquals("Great portfolio!", result.getContent());
        verify(interactionRepository, times(1)).save(any(Interaction.class));
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
        verify(activityFeedService, times(1)).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void addComment_WithEmptyContent_ShouldThrowException() {
        // Arrange
        commentRequest.setContent("   ");
        Portfolio portfolio = new Portfolio();
        portfolio.setOwnerId(UUID.randomUUID().toString());
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> interactionService.addComment(actorId, targetId, commentRequest));
        verify(interactionRepository, never()).save(any());
    }

    @Test
    void toggleLike_WithMissingPortfolio_ShouldThrowNotFound() {
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> interactionService.toggleLike(actorId, targetId, likeRequest));

        assertEquals("Portfolio not found", ex.getMessage());
        verify(interactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(activityFeedService, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void addComment_WithTooLongContent_ShouldThrowException() {
        Portfolio portfolio = new Portfolio();
        portfolio.setOwnerId(UUID.randomUUID().toString());
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio));
        commentRequest.setContent("x".repeat(1001));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> interactionService.addComment(actorId, targetId, commentRequest));

        assertEquals("Comment content cannot exceed 1000 characters", ex.getMessage());
        verify(interactionRepository, never()).save(any());
    }
}
