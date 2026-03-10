package com.finance.core.service;

import com.finance.core.domain.ActivityEvent;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.Interaction;
import com.finance.core.domain.Notification.NotificationType;
import com.finance.core.domain.event.NotificationEvent;
import com.finance.core.dto.CommentResponse;
import com.finance.core.dto.InteractionRequest;
import com.finance.core.repository.AnalysisPostRepository;
import com.finance.core.repository.InteractionRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final AuditLogService auditLogService;

    @Transactional
    public void toggleLike(UUID actorId, UUID targetId, InteractionRequest request) {
        Interaction.TargetType targetType = parseTargetType(request.getTargetType());
        TargetMetadata target = resolveTarget(targetType, targetId);

        Optional<Interaction> existing = interactionRepository.findByActorIdAndTargetTypeAndTargetIdAndInteractionType(
                actorId, targetType, targetId, Interaction.InteractionType.LIKE);

        if (existing.isPresent()) {
            interactionRepository.delete(existing.get());
            auditLogService.record(
                    actorId,
                    AuditActionType.INTERACTION_UNLIKED,
                    mapAuditResourceType(targetType),
                    targetId,
                    buildInteractionDetails(targetType, target.label(), null, true));
            log.info("User {} UNLIKED {} {}", actorId, targetType, targetId);
            return;
        }

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
            log.debug("Duplicate LIKE ignored for actor {} target {} {}", actorId, targetType, targetId);
            return;
        }
        log.info("User {} LIKED {} {}", actorId, targetType, targetId);

        sendNotification(actor, target, targetId, true);
        publishActivity(actor, target, targetId, true);
        auditLogService.record(
                actorId,
                AuditActionType.INTERACTION_LIKED,
                mapAuditResourceType(targetType),
                targetId,
                buildInteractionDetails(targetType, target.label(), null, false));
    }

    @Transactional
    public Interaction addComment(UUID actorId, UUID targetId, InteractionRequest request) {
        Interaction.TargetType targetType = parseTargetType(request.getTargetType());
        TargetMetadata target = resolveTarget(targetType, targetId);

        String content = request.getContent() != null ? request.getContent().trim() : "";
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Comment content cannot be empty");
        }
        if (content.length() > 1000) {
            throw new IllegalArgumentException("Comment content cannot exceed 1000 characters");
        }

        AppUser actor = getActor(actorId);

        Interaction comment = Interaction.builder()
                .actorId(actorId)
                .interactionType(Interaction.InteractionType.COMMENT)
                .targetType(targetType)
                .targetId(targetId)
                .content(content)
                .build();

        comment = interactionRepository.save(comment);
        log.info("User {} COMMENTED on {} {}", actorId, targetType, targetId);

        sendNotification(actor, target, targetId, false);
        publishActivity(actor, target, targetId, false);
        auditLogService.record(
                actorId,
                AuditActionType.INTERACTION_COMMENTED,
                mapAuditResourceType(targetType),
                targetId,
                buildInteractionDetails(targetType, target.label(), content, false));

        return comment;
    }

    public Page<CommentResponse> getComments(UUID targetId, String targetTypeStr, UUID requesterId, Pageable pageable) {
        Interaction.TargetType targetType = parseTargetType(targetTypeStr);
        Page<Interaction> comments = interactionRepository.findByTargetTypeAndTargetIdAndInteractionTypeOrderByCreatedAtDesc(
                targetType, targetId, Interaction.InteractionType.COMMENT, pageable);

        Set<UUID> actorIds = comments.getContent().stream()
                .map(Interaction::getActorId)
                .collect(Collectors.toSet());
        Map<UUID, AppUser> actorMap = userRepository.findByIdIn(actorIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        return comments.map(comment -> toCommentResponse(comment, requesterId, actorMap));
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
            throw new IllegalArgumentException("Invalid target type. Use PORTFOLIO, ANALYSIS_POST or COMMENT");
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
                    ActivityEvent.EventType.PORTFOLIO_COMMENTED,
                    targetId);
        }

        if (targetType == Interaction.TargetType.ANALYSIS_POST) {
            var post = analysisPostRepository.findById(targetId)
                    .orElseThrow(() -> new RuntimeException("Analysis post not found"));
            return new TargetMetadata(
                    post.getAuthorId(),
                    post.getTitle(),
                    ActivityEvent.TargetType.POST,
                    NotificationType.POST_LIKE,
                    NotificationType.POST_COMMENT,
                    ActivityEvent.EventType.POST_LIKED,
                    ActivityEvent.EventType.POST_COMMENTED,
                    targetId);
        }

        Interaction comment = interactionRepository.findByIdAndInteractionType(targetId, Interaction.InteractionType.COMMENT)
                .orElseThrow(() -> new RuntimeException("Comment not found"));
        RootTargetMetadata rootTarget = resolveRootTarget(comment);
        return new TargetMetadata(
                comment.getActorId(),
                rootTarget.label(),
                rootTarget.activityTargetType(),
                rootTarget.likeNotificationType(),
                rootTarget.commentNotificationType(),
                rootTarget.likeEventType(),
                rootTarget.commentEventType(),
                rootTarget.referenceId());
    }

    private RootTargetMetadata resolveRootTarget(Interaction comment) {
        if (comment.getTargetType() == Interaction.TargetType.PORTFOLIO) {
            var portfolio = portfolioRepository.findById(comment.getTargetId())
                    .orElseThrow(() -> new RuntimeException("Portfolio not found"));
            return new RootTargetMetadata(
                    portfolio.getName(),
                    ActivityEvent.TargetType.PORTFOLIO,
                    NotificationType.PORTFOLIO_LIKE,
                    NotificationType.PORTFOLIO_COMMENT,
                    ActivityEvent.EventType.PORTFOLIO_LIKED,
                    ActivityEvent.EventType.PORTFOLIO_COMMENTED,
                    comment.getTargetId());
        }

        if (comment.getTargetType() == Interaction.TargetType.ANALYSIS_POST) {
            var post = analysisPostRepository.findById(comment.getTargetId())
                    .orElseThrow(() -> new RuntimeException("Analysis post not found"));
            return new RootTargetMetadata(
                    post.getTitle(),
                    ActivityEvent.TargetType.POST,
                    NotificationType.POST_LIKE,
                    NotificationType.POST_COMMENT,
                    ActivityEvent.EventType.POST_LIKED,
                    ActivityEvent.EventType.POST_COMMENTED,
                    comment.getTargetId());
        }

        Interaction parentComment = interactionRepository
                .findByIdAndInteractionType(comment.getTargetId(), Interaction.InteractionType.COMMENT)
                .orElseThrow(() -> new RuntimeException("Parent comment not found"));
        return resolveRootTarget(parentComment);
    }

    private void sendNotification(AppUser actor, TargetMetadata target, UUID targetId, boolean isLike) {
        NotificationType type = isLike ? target.likeNotificationType() : target.commentNotificationType();
        if (actor.getId().equals(target.ownerId())) {
            return;
        }

        NotificationEvent event = NotificationEvent.builder()
                .receiverId(target.ownerId())
                .actorId(actor.getId())
                .actorUsername(actor.getUsername())
                .type(type)
                .referenceId(target.referenceId() != null ? target.referenceId() : targetId)
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
                target.referenceId() != null ? target.referenceId() : targetId,
                target.label());
    }

    private CommentResponse toCommentResponse(Interaction comment, UUID requesterId, Map<UUID, AppUser> actorMap) {
        AppUser actor = actorMap.get(comment.getActorId());
        long likeCount = getLikeCount(comment.getId(), Interaction.TargetType.COMMENT.name());
        boolean hasLiked = requesterId != null && hasLiked(requesterId, comment.getId(), Interaction.TargetType.COMMENT.name());
        long replyCount = interactionRepository.countByTargetTypeAndTargetIdAndInteractionType(
                Interaction.TargetType.COMMENT, comment.getId(), Interaction.InteractionType.COMMENT);

        return CommentResponse.builder()
                .id(comment.getId())
                .actorId(comment.getActorId())
                .actorUsername(actor != null ? actor.getUsername() : "unknown")
                .actorDisplayName(actor != null && actor.getDisplayName() != null
                        ? actor.getDisplayName()
                        : actor != null ? actor.getUsername() : "Unknown")
                .actorAvatarUrl(actor != null ? actor.getAvatarUrl() : null)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .likeCount(likeCount)
                .hasLiked(hasLiked)
                .replyCount(replyCount)
                .build();
    }

    private record TargetMetadata(
            UUID ownerId,
            String label,
            ActivityEvent.TargetType activityTargetType,
            NotificationType likeNotificationType,
            NotificationType commentNotificationType,
            ActivityEvent.EventType likeEventType,
            ActivityEvent.EventType commentEventType,
            UUID referenceId) {
    }

    private record RootTargetMetadata(
            String label,
            ActivityEvent.TargetType activityTargetType,
            NotificationType likeNotificationType,
            NotificationType commentNotificationType,
            ActivityEvent.EventType likeEventType,
            ActivityEvent.EventType commentEventType,
            UUID referenceId) {
    }

    private AuditResourceType mapAuditResourceType(Interaction.TargetType targetType) {
        return switch (targetType) {
            case PORTFOLIO -> AuditResourceType.PORTFOLIO;
            case ANALYSIS_POST -> AuditResourceType.ANALYSIS_POST;
            case COMMENT -> AuditResourceType.COMMENT;
        };
    }

    private Map<String, Object> buildInteractionDetails(
            Interaction.TargetType targetType,
            String label,
            String content,
            boolean removed) {

        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("targetType", targetType.name());
        details.put("targetLabel", label);
        details.put("removed", removed);
        if (content != null) {
            details.put("contentPreview", content);
        }
        return details;
    }
}
