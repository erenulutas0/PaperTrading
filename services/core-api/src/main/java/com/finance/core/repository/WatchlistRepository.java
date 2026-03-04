package com.finance.core.repository;

import com.finance.core.domain.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, UUID> {

    List<Watchlist> findByUserId(UUID userId);

    Optional<Watchlist> findByIdAndUserId(UUID id, UUID userId);
}
