package com.finance.core.repository;

import com.finance.core.domain.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, UUID> {
        List<PortfolioSnapshot> findByPortfolioIdOrderByTimestampAsc(UUID portfolioId);

        Optional<PortfolioSnapshot> findFirstByPortfolioIdOrderByTimestampAsc(UUID portfolioId);

        Optional<PortfolioSnapshot> findFirstByPortfolioIdAndTimestampBeforeOrderByTimestampDesc(UUID portfolioId,
                        LocalDateTime timestamp);

        Optional<PortfolioSnapshot> findFirstByPortfolioIdAndTimestampLessThanEqualOrderByTimestampDesc(
                        UUID portfolioId,
                        LocalDateTime timestamp);

        Optional<PortfolioSnapshot> findFirstByPortfolioIdAndTimestampGreaterThanEqualOrderByTimestampAsc(
                        UUID portfolioId,
                        LocalDateTime timestamp);
}
