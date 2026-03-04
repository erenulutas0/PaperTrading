package com.finance.core.repository;

import com.finance.core.domain.PortfolioParticipant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioParticipantRepository extends JpaRepository<PortfolioParticipant, UUID> {

    /** Check if a user has already joined a portfolio */
    boolean existsByPortfolioIdAndUserId(UUID portfolioId, UUID userId);

    /** Find a specific participation record */
    Optional<PortfolioParticipant> findByPortfolioIdAndUserId(UUID portfolioId, UUID userId);

    /** Paginated list of participants for a portfolio */
    Page<PortfolioParticipant> findByPortfolioId(UUID portfolioId, Pageable pageable);

    /** Count participants for a portfolio */
    long countByPortfolioId(UUID portfolioId);

    /** Count how many portfolios a user has joined */
    long countByUserId(UUID userId);
}
