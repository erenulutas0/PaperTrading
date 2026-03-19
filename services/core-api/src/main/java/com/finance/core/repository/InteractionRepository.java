package com.finance.core.repository;

import com.finance.core.domain.Interaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InteractionRepository extends JpaRepository<Interaction, UUID> {
    Page<Interaction> findByTargetTypeAndTargetIdAndInteractionTypeOrderByCreatedAtDesc(
            Interaction.TargetType targetType, UUID targetId, Interaction.InteractionType type, Pageable pageable);

    long countByTargetTypeAndTargetIdAndInteractionType(Interaction.TargetType targetType, UUID targetId,
            Interaction.InteractionType type);

    Optional<Interaction> findByActorIdAndTargetTypeAndTargetIdAndInteractionType(
            UUID actorId, Interaction.TargetType targetType, UUID targetId, Interaction.InteractionType type);

    boolean existsByActorIdAndTargetTypeAndTargetIdAndInteractionType(
            UUID actorId, Interaction.TargetType targetType, UUID targetId, Interaction.InteractionType type);

    Optional<Interaction> findByIdAndInteractionType(UUID id, Interaction.InteractionType type);

    Collection<Interaction> findByTargetTypeAndTargetIdAndInteractionType(
            Interaction.TargetType targetType, UUID targetId, Interaction.InteractionType type);

    @Query(value = """
            select
                i.id as id,
                i.actorId as actorId,
                coalesce(u.username, 'unknown') as actorUsername,
                coalesce(u.displayName, u.username, 'Unknown') as actorDisplayName,
                u.avatarUrl as actorAvatarUrl,
                i.content as content,
                i.createdAt as createdAt,
                (select count(l)
                 from Interaction l
                 where l.targetType = :commentTargetType
                   and l.targetId = i.id
                   and l.interactionType = :likeType) as likeCount,
                case
                    when :requesterId is not null and exists (
                        select 1
                        from Interaction liked
                        where liked.actorId = :requesterId
                          and liked.targetType = :commentTargetType
                          and liked.targetId = i.id
                          and liked.interactionType = :likeType
                    ) then true
                    else false
                end as hasLiked,
                (select count(r)
                 from Interaction r
                 where r.targetType = :commentTargetType
                   and r.targetId = i.id
                   and r.interactionType = :commentType) as replyCount
            from Interaction i
            left join AppUser u on u.id = i.actorId
            where i.targetType = :targetType
              and i.targetId = :targetId
              and i.interactionType = :interactionType
            order by i.createdAt desc
            """,
            countQuery = """
            select count(i)
            from Interaction i
            where i.targetType = :targetType
              and i.targetId = :targetId
              and i.interactionType = :interactionType
            """)
    Page<CommentRowView> findCommentRows(
            @Param("targetType") Interaction.TargetType targetType,
            @Param("targetId") UUID targetId,
            @Param("interactionType") Interaction.InteractionType interactionType,
            @Param("commentTargetType") Interaction.TargetType commentTargetType,
            @Param("commentType") Interaction.InteractionType commentType,
            @Param("likeType") Interaction.InteractionType likeType,
            @Param("requesterId") UUID requesterId,
            Pageable pageable);

    @Query("""
            select i.targetId as targetId, count(i) as totalCount
            from Interaction i
            where i.targetType = :targetType
              and i.targetId in :targetIds
              and i.interactionType = :interactionType
            group by i.targetId
            """)
    List<InteractionAggregateView> aggregateCountsByTargetIds(
            @Param("targetType") Interaction.TargetType targetType,
            @Param("targetIds") Collection<UUID> targetIds,
            @Param("interactionType") Interaction.InteractionType interactionType);

    @Query("""
            select i.targetId
            from Interaction i
            where i.actorId = :actorId
              and i.targetType = :targetType
              and i.targetId in :targetIds
              and i.interactionType = :interactionType
            """)
    List<UUID> findTargetIdsLikedByActor(
            @Param("actorId") UUID actorId,
            @Param("targetType") Interaction.TargetType targetType,
            @Param("targetIds") Collection<UUID> targetIds,
            @Param("interactionType") Interaction.InteractionType interactionType);

    interface InteractionAggregateView {
        UUID getTargetId();
        long getTotalCount();
    }

    interface CommentRowView {
        UUID getId();
        UUID getActorId();
        String getActorUsername();
        String getActorDisplayName();
        String getActorAvatarUrl();
        String getContent();
        java.time.LocalDateTime getCreatedAt();
        long getLikeCount();
        boolean getHasLiked();
        long getReplyCount();
    }
}
