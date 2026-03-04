package com.finance.core.repository;

import com.finance.core.domain.ActivityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

    /** Global feed: all events, newest first (paginated) */
    Page<ActivityEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Personalized feed for a follower without loading all follow IDs in memory */
    @Query("""
            SELECT e
            FROM ActivityEvent e
            WHERE e.actorId IN (
                SELECT f.followingId
                FROM Follow f
                WHERE f.followerId = :followerId
            )
            ORDER BY e.createdAt DESC
            """)
    Page<ActivityEvent> findPersonalizedFeedByFollowerId(
            @Param("followerId") UUID followerId, Pageable pageable);

    /** User's own activity */
    Page<ActivityEvent> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);
}
