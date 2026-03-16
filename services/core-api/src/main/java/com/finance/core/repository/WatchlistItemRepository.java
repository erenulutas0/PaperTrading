package com.finance.core.repository;

import com.finance.core.domain.WatchlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {

    List<WatchlistItem> findByWatchlistId(UUID watchlistId);
    Page<WatchlistItem> findByWatchlistIdOrderByAddedAtAsc(UUID watchlistId, Pageable pageable);

    /**
     * Find all items across all watchlists that have active (untriggered) price
     * alerts
     */
    @Query("SELECT wi FROM WatchlistItem wi WHERE " +
            "(wi.alertPriceAbove IS NOT NULL AND wi.alertAboveTriggered = false) OR " +
            "(wi.alertPriceBelow IS NOT NULL AND wi.alertBelowTriggered = false)")
    List<WatchlistItem> findAllWithActiveAlerts();

    Optional<WatchlistItem> findByIdAndWatchlistUserId(UUID id, UUID userId);
}
