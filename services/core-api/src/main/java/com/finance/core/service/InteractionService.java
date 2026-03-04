package com.finance.core.service;

import com.finance.core.domain.ActivityEvent;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.Interaction;
import com.finance.core.domain.Notification.NotificationType;
import com.finance.core.domain.event.NotificationEvent;
import com.finance.core.dto.InteractionRequest;
import com.finance.core.repository.AnalysisPostRepository;
import com.finance.core.repository.InteractionRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InteractionService {

    private final InteractionRepository interactionRepository;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final AnalysisPostRepository analysisPostRepository;
    private final ActivityFeedService activityFeedService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void toggleLike(UUID actorId, UUID targetId, InteractionRequest request) {
        Interaction.TargetType targetType = parseTargetType(request.getTargetType());
        TargetMetadata target = resolveTarget(targetType, targetId);

        Optional<Interaction> existing = interactionRepository.findByActorIdAndTargetTypeAndTargetIdAndInteractionType(
                actorId, targetType, targetId, Interaction.InteractionType.LIKE);

        if (existing.isPresent()) {
            // Unlike
            interactionRepository.delete(existing.get());
            log.info("User {} UNLIKED {} {}", actorId, targetType, targetId);
        } else {
            // Like
            AppUser actor = getActor(actorId);
            Interaction like = Interaction.builder()
                    .actorId(actorId)
                    .interactionType(Interaction.InteractionType.LIKE)
                    .targetType(targetType)
                    .targetId(targetId)
                    .build();
            try {
                interactionRepository.save(like);
            } catch (DataIntegrityViolationException e) {
                // Concurrent like toggles can race; unique DB constraint is the source of truth.
                log.debug("Duplicate LIKE ignored for actor {} target {} {}", actorId, targetType, targetId);
                return;
            }
            log.info("User {} LIKED {} {}", actorId, targetType, targetId);

            sendNotification(actor, target, targetId, true);
            publishActivity(actor, target, targetId, true);
        }
    }

    @Transactional
    public Interaction addComment(UUID actorId, UUID targetId, InteractionRequest request) {
        Interaction.TargetType targetType = parseTargetType(request.getTargetType());
        TargetMetadata target = resolveTarget(targetType, targetId);

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Comment content cannot be empty");
        }
        if (request.getContent().trim().length() > 1000) {
            throw new IllegalArgumentException("Comment content cannot exceed 1000 characters");
        }

        AppUser actor = getActor(actorId);

        Interaction comment = Interaction.builder()
                .actorId(actorId)
                .interactionType(Interaction.InteractionType.COMMENT)
                .targetType(targetType)
                .targetId(targetId)
                .content(request.getContent().trim())
                .build();

        comment = interactionRepository.save(comment);
        log.info("User {} COMMENTED on {} {}", actorId, targetType, targetId);

        sendNotification(actor, target, targetId, false);
        publishActivity(actor, target, targetId, false);

        return comment;
    }

    public Page<Interaction> getComments(UUID targetId, String targetTypeStr, Pageable pageable) {
        Interaction.TargetType targetType = parseTargetType(targetTypeStr);
        return interactionRepository.findByTargetTypeAndTargetIdAndInteractionTypeOrderByCreatedAtDesc(
                targetType, targetId, Interaction.InteractionType.COMMENT, pageable);
    }

    public long getLikeCount(UUID targetId, String targetTypeStr) {
        Interaction.TargetType targetType = parseTargetType(targetTypeStr);
        return interactionRepository.countByTargetTypeAndTargetIdAndInteractionType(
                targetType, targetId, Interaction.InteractionType.LIKE);
    }

    public boolean hasLiked(UUID actorId, UUID targetId, String targetTypeStr) {
        Interaction.TargetType targetType = parseTargetType(targetTypeStr);
        return interactionRepository.existsByActorIdAndTargetTypeAndTargetIdAndInteractionType(
                actorId, targetType, targetId, Interaction.InteractionType.LIKE);
    }

    private Interaction.TargetType parseTargetType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            throw new IllegalArgumentException("Target type is required");
        }
        try {
            return Interaction.TargetType.valueOf(typeStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid target type. Use PORTFOLIO or ANALYSIS_POST");
        }
    }

    private AppUser getActor(UUID actorId) {
        return userRepository.findById(actorId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private TargetMetadata resolveTarget(Interaction.TargetType targetType, UUID targetId) {
        if (targetType == Interaction.TargetType.PORTFOLIO) {
            var portfolio = portfolioRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("Portfolio not found"));
            UUID ownerId;
            try {
                ownerId = UUID.fromString(portfolio.getOwnerId());
            } catch (Exception e) {
                throw new RuntimeException("Portfolio owner is invalid");
            }
            return new TargetMetadata(
                    ownerId,
                    portfolio.getName(),
                    ActivityEvent.TargetType.PORTFOLIO,
                    NotificationType.PORTFOLIO_LIKE,
                    NotificationType.PORTFOLIO_COMMENT,
                    ActivityEvent.EventType.PORTFOLIO_LIKED,
                    ActivityEvent.EventType.PORTFOLIO_COMMENTED);
        }

        var post = analysisPostRepository.findById(targetId)
                .orElseThrow(() -> new RuntimeException("Analysis post not found"));
        return new TargetMetadata(
                post.getAuthorId(),
                post.getTitle(),
                ActivityEvent.TargetType.POST,
                NotificationType.POST_LIKE,
                NotificationType.POST_COMMENT,
                ActivityEvent.EventType.POST_LIKED,
                ActivityEvent.EventType.POST_COMMENTED);
    }

    private void sendNotification(AppUser actor, TargetMetadata target, UUID targetId, boolean isLike) {
        NotificationType type = isLike ? target.likeNotificationType() : target.commentNotificationType();
        NotificationEvent event = NotificationEvent.builder()
                .receiverId(target.ownerId())
                .actorId(actor.getId())
                .actorUsername(actor.getUsername())
                .type(type)
                .referenceId(targetId)
                .referenceLabel(target.label())
                .build();
        eventPublisher.publishEvent(event);
    }

    private void publishActivity(AppUser actor, TargetMetadata target, UUID targetId, boolean isLike) {
        ActivityEvent.EventType eventType = isLike ? target.likeEventType() : target.commentEventType();
        activityFeedService.publish(
                actor.getId(),
                actor.getUsername(),
                eventType,
                target.activityTargetType(),
                targetId,
                target.label());
    }

    private record TargetMetadata(
            UUID ownerId,
            String label,
            ActivityEvent.TargetType activityTargetType,
            NotificationType likeNotificationType,
            NotificationType commentNotificationType,
            ActivityEvent.EventType likeEventType,
            ActivityEvent.EventType commentEventType) {
    }
}
