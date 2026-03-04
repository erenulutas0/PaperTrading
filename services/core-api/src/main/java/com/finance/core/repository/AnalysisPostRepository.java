package com.finance.core.repository;

import com.finance.core.domain.AnalysisPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface AnalysisPostRepository extends JpaRepository<AnalysisPost, UUID> {

        /** All posts by a specific author, latest first (paginated) */
        Page<AnalysisPost> findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(UUID authorId, Pageable pageable);

        /** Legacy list method (for tests/migration) */
        List<AnalysisPost> findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(UUID authorId);

        /** Global feed — all non-deleted posts, latest first (paginated) */
        Page<AnalysisPost> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

        /** Legacy list method */
        List<AnalysisPost> findByDeletedFalseOrderByCreatedAtDesc();

        /**
         * All PENDING posts whose targetDate has passed — candidates for EXPIRED
         * resolution
         */
        List<AnalysisPost> findByOutcomeAndTargetDateBeforeAndDeletedFalse(
                        AnalysisPost.Outcome outcome, LocalDateTime now);

        /** All PENDING posts for a specific symbol — candidates for HIT/MISSED check */
        List<AnalysisPost> findByOutcomeAndInstrumentSymbolAndDeletedFalse(
                        AnalysisPost.Outcome outcome, String symbol);

        /** Count non-deleted posts by author */
        long countByAuthorIdAndDeletedFalse(UUID authorId);

        /** Count resolved (HIT) posts by author — for trust score */
        long countByAuthorIdAndOutcomeAndDeletedFalse(UUID authorId, AnalysisPost.Outcome outcome);
}
