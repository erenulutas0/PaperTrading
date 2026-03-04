package com.finance.core.repository;

import com.finance.core.domain.Interaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
