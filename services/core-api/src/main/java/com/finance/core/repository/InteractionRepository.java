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
}
