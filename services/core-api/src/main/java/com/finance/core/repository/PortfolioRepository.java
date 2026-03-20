package com.finance.core.repository;

import com.finance.core.domain.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    @Query("SELECT DISTINCT p FROM Portfolio p LEFT JOIN FETCH p.items")
    List<Portfolio> findAllWithItems();

    @Query(value = "SELECT p.id FROM Portfolio p",
            countQuery = "SELECT COUNT(p) FROM Portfolio p")
    Page<UUID> findAllIds(Pageable pageable);

    @EntityGraph(attributePaths = "items")
    List<Portfolio> findByOwnerId(String ownerId);

    @Query(value = "SELECT p.id FROM Portfolio p WHERE p.ownerId = :ownerId",
            countQuery = "SELECT COUNT(p) FROM Portfolio p WHERE p.ownerId = :ownerId")
    Page<UUID> findIdsByOwnerId(@Param("ownerId") String ownerId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    List<Portfolio> findByVisibility(Portfolio.Visibility visibility);

    @Query(value = """
            SELECT p.id FROM Portfolio p
            WHERE p.visibility = :visibility
              AND (
                :query = ''
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR EXISTS (
                    SELECT 1 FROM PortfolioItem pi
                    WHERE pi.portfolio = p
                      AND LOWER(pi.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                )
              )
            """,
            countQuery = """
                    SELECT COUNT(p) FROM Portfolio p
                    WHERE p.visibility = :visibility
                      AND (
                        :query = ''
                        OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR EXISTS (
                            SELECT 1 FROM PortfolioItem pi
                            WHERE pi.portfolio = p
                              AND LOWER(pi.symbol) LIKE LOWER(CONCAT('%', :query, '%'))
                        )
                      )
                    """)
    Page<UUID> searchDiscoverableIdsByVisibility(
            @Param("visibility") Portfolio.Visibility visibility,
            @Param("query") String query,
            Pageable pageable);

    @Query(value = "SELECT p.id FROM Portfolio p WHERE p.visibility = :visibility",
            countQuery = "SELECT COUNT(p) FROM Portfolio p WHERE p.visibility = :visibility")
    Page<UUID> findIdsByVisibility(@Param("visibility") Portfolio.Visibility visibility, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    List<Portfolio> findByOwnerIdAndVisibility(String ownerId, Portfolio.Visibility visibility);

    @EntityGraph(attributePaths = "items")
    List<Portfolio> findByOwnerIdInAndVisibility(Collection<String> ownerIds, Portfolio.Visibility visibility);

    @EntityGraph(attributePaths = "items")
    List<Portfolio> findByIdInAndVisibility(Collection<UUID> ids, Portfolio.Visibility visibility);

    @EntityGraph(attributePaths = "items")
    List<Portfolio> findByIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = "items")
    Optional<Portfolio> findWithItemsById(UUID id);
}
