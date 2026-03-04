package com.finance.core.repository;

import com.finance.core.domain.TournamentParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipant, UUID> {

    boolean existsByTournamentIdAndUserId(UUID tournamentId, UUID userId);

    Optional<TournamentParticipant> findByTournamentIdAndUserId(UUID tournamentId, UUID userId);

    List<TournamentParticipant> findByTournamentId(UUID tournamentId);

    List<TournamentParticipant> findByTournamentIdOrderByFinalRankAsc(UUID tournamentId);

    List<TournamentParticipant> findByUserId(UUID userId);

    long countByTournamentId(UUID tournamentId);

    @Query("SELECT p.portfolioId FROM TournamentParticipant p WHERE p.tournamentId = :tournamentId")
    List<UUID> findAllPortfolioIdsByTournamentId(@Param("tournamentId") UUID tournamentId);

    Optional<TournamentParticipant> findByPortfolioId(UUID portfolioId);
}
