package com.finance.core.repository;

import com.finance.core.domain.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    @Query("SELECT DISTINCT p FROM Portfolio p LEFT JOIN FETCH p.items")
    List<Portfolio> findAllWithItems();

    @EntityGraph(attributePaths = "items")
    Page<Portfolio> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "items")
    List<Portfolio> findByOwnerId(String ownerId);

    @EntityGraph(attributePaths = "items")
    Page<Portfolio> findByOwnerId(String ownerId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    List<Portfolio> findByVisibility(Portfolio.Visibility visibility);

    @EntityGraph(attributePaths = "items")
    Page<Portfolio> findByVisibility(Portfolio.Visibility visibility, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    List<Portfolio> findByOwnerIdAndVisibility(String ownerId, Portfolio.Visibility visibility);

    @EntityGraph(attributePaths = "items")
    List<Portfolio> findByIdInAndVisibility(Collection<UUID> ids, Portfolio.Visibility visibility);

    @EntityGraph(attributePaths = "items")
    Page<Portfolio> findByOwnerIdAndVisibility(String ownerId, Portfolio.Visibility visibility, Pageable pageable);
}
