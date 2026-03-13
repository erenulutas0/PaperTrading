package com.finance.core.repository;

import com.finance.core.domain.WatchlistAlertEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WatchlistAlertEventRepository extends JpaRepository<WatchlistAlertEvent, UUID> {

    List<WatchlistAlertEvent> findByWatchlistItemIdOrderByTriggeredAtDesc(UUID watchlistItemId, Pageable pageable);
}
