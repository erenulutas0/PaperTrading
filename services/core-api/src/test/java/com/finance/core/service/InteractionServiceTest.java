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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(interactionRepository.findByActorIdAndTargetTypeAndTargetIdAndInteractionType(
                actorId, Interaction.TargetType.PORTFOLIO, targetId, Interaction.InteractionType.LIKE))
                .thenReturn(Optional.empty());

        when(userRepository.findById(actorId)).thenReturn(Optional.of(AppUser.builder().id(actorId).username("actor").build()));
        Portfolio portfolio = portfolio("Growth");
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio));

        interactionService.toggleLike(actorId, targetId, likeRequest);

        verify(interactionRepository).save(any(Interaction.class));
        verify(eventPublisher).publishEvent(any(Object.class));
        verify(activityFeedService).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void toggleLike_WhenConcurrentDuplicateLike_ShouldBeIdempotent() {
        when(interactionRepository.findByActorIdAndTargetTypeAndTargetIdAndInteractionType(
                actorId, Interaction.TargetType.PORTFOLIO, targetId, Interaction.InteractionType.LIKE))
                .thenReturn(Optional.empty());

        when(userRepository.findById(actorId)).thenReturn(Optional.of(AppUser.builder().id(actorId).username("actor").build()));
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio("Race Portfolio")));
        when(interactionRepository.save(any(Interaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        interactionService.toggleLike(actorId, targetId, likeRequest);

        verify(eventPublisher, never()).publishEvent(any());
        verify(activityFeedService, never()).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void toggleLike_WhenAlreadyLiked_ShouldDeleteLike() {
        Interaction existingLike = new Interaction();
        when(interactionRepository.findByActorIdAndTargetTypeAndTargetIdAndInteractionType(
                actorId, Interaction.TargetType.PORTFOLIO, targetId, Interaction.InteractionType.LIKE))
                .thenReturn(Optional.of(existingLike));
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio("Growth")));

        interactionService.toggleLike(actorId, targetId, likeRequest);

        verify(interactionRepository).delete(existingLike);
        verify(interactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void addComment_WithValidContent_ShouldSaveComment() {
        when(userRepository.findById(actorId)).thenReturn(Optional.of(AppUser.builder().id(actorId).username("actor").build()));
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio("Growth")));

        Interaction savedComment = Interaction.builder()
                .content("Great portfolio!")
                .build();
        when(interactionRepository.save(any())).thenReturn(savedComment);

        Interaction result = interactionService.addComment(actorId, targetId, commentRequest);

        assertNotNull(result);
        assertEquals("Great portfolio!", result.getContent());
        verify(interactionRepository).save(any(Interaction.class));
        verify(eventPublisher).publishEvent(any(Object.class));
        verify(activityFeedService).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    void addComment_WithEmptyContent_ShouldThrowException() {
        commentRequest.setContent("   ");
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio("Growth")));

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
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio("Growth")));
        commentRequest.setContent("x".repeat(1001));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> interactionService.addComment(actorId, targetId, commentRequest));

        assertEquals("Comment content cannot exceed 1000 characters", ex.getMessage());
        verify(interactionRepository, never()).save(any());
    }

    @Test
    void addReplyToComment_ShouldResolveCommentTargetAndNotifyCommentOwner() {
        UUID commentId = UUID.randomUUID();
        UUID commentOwnerId = UUID.randomUUID();

        InteractionRequest replyRequest = new InteractionRequest();
        replyRequest.setTargetType("COMMENT");
        replyRequest.setContent("Replying to this take");

        Interaction parentComment = Interaction.builder()
                .id(commentId)
                .actorId(commentOwnerId)
                .interactionType(Interaction.InteractionType.COMMENT)
                .targetType(Interaction.TargetType.PORTFOLIO)
                .targetId(targetId)
                .content("Original comment")
                .build();

        when(interactionRepository.findByIdAndInteractionType(commentId, Interaction.InteractionType.COMMENT))
                .thenReturn(Optional.of(parentComment));
        when(portfolioRepository.findById(targetId)).thenReturn(Optional.of(portfolio("Growth")));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(AppUser.builder().id(actorId).username("reply_user").build()));
        when(interactionRepository.save(any(Interaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Interaction reply = interactionService.addComment(actorId, commentId, replyRequest);

        assertEquals(Interaction.TargetType.COMMENT, reply.getTargetType());
        assertEquals(commentId, reply.getTargetId());
        verify(eventPublisher).publishEvent(any());
        verify(activityFeedService).publish(any(), any(), any(), any(), eq(targetId), eq("Growth"));
    }

    @Test
    void getComments_ShouldReturnCommentDtoWithReplyAndLikeMetadata() {
        UUID commentId = UUID.randomUUID();
        AppUser actor = AppUser.builder()
                .id(actorId)
                .username("commenter")
                .displayName("Commenter")
                .build();
        Interaction comment = Interaction.builder()
                .id(commentId)
                .actorId(actorId)
                .interactionType(Interaction.InteractionType.COMMENT)
                .targetType(Interaction.TargetType.PORTFOLIO)
                .targetId(targetId)
                .content("Root comment")
                .build();

        when(interactionRepository.findByTargetTypeAndTargetIdAndInteractionTypeOrderByCreatedAtDesc(
                eq(Interaction.TargetType.PORTFOLIO), eq(targetId), eq(Interaction.InteractionType.COMMENT), any()))
                .thenReturn(new PageImpl<>(List.of(comment)));
        when(userRepository.findByIdIn(any())).thenReturn(List.of(actor));
        when(interactionRepository.aggregateCountsByTargetIds(
                Interaction.TargetType.COMMENT,
                Set.of(commentId),
                Interaction.InteractionType.LIKE))
                .thenReturn(List.of(aggregate(commentId, 2L)));
        when(interactionRepository.findTargetIdsLikedByActor(
                actorId,
                Interaction.TargetType.COMMENT,
                Set.of(commentId),
                Interaction.InteractionType.LIKE))
                .thenReturn(List.of(commentId));
        when(interactionRepository.aggregateCountsByTargetIds(
                Interaction.TargetType.COMMENT,
                Set.of(commentId),
                Interaction.InteractionType.COMMENT))
                .thenReturn(List.of(aggregate(commentId, 1L)));

        var page = interactionService.getComments(targetId, "PORTFOLIO", actorId, PageRequest.of(0, 20));

        assertEquals(1, page.getContent().size());
        assertEquals("commenter", page.getContent().get(0).getActorUsername());
        assertEquals(2L, page.getContent().get(0).getLikeCount());
        assertEquals(1L, page.getContent().get(0).getReplyCount());
        assertTrue(page.getContent().get(0).isHasLiked());
    }

    private InteractionRepository.InteractionAggregateView aggregate(UUID targetId, long totalCount) {
        return new InteractionRepository.InteractionAggregateView() {
            @Override
            public UUID getTargetId() {
                return targetId;
            }

            @Override
            public long getTotalCount() {
                return totalCount;
            }
        };
    }

    private Portfolio portfolio(String name) {
        Portfolio portfolio = new Portfolio();
        portfolio.setOwnerId(UUID.randomUUID().toString());
        portfolio.setName(name);
        return portfolio;
    }
}
