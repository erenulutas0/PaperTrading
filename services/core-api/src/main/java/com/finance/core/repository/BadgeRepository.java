package com.finance.core.repository;

import com.finance.core.domain.Badge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, UUID> {

    List<Badge> findByUserId(UUID userId);

    List<Badge> findByUserIdOrderByEarnedAtDesc(UUID userId);

    Page<Badge> findByUserIdOrderByEarnedAtDesc(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);
}
