package com.finance.core.repository;

import com.finance.core.domain.Tournament;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, UUID> {

    List<Tournament> findByStatus(Tournament.Status status);

    Page<Tournament> findByStatus(Tournament.Status status, Pageable pageable);

    /** Find tournaments that should transition to ACTIVE */
    List<Tournament> findByStatusAndStartsAtBefore(Tournament.Status status, LocalDateTime now);

    /** Find tournaments that should transition to COMPLETED */
    List<Tournament> findByStatusAndEndsAtBefore(Tournament.Status status, LocalDateTime now);

    /** All tournaments ordered by start date desc */
    List<Tournament> findAllByOrderByStartsAtDesc();

    Page<Tournament> findAllByOrderByStartsAtDesc(Pageable pageable);
}
