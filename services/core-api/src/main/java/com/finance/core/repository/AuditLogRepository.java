package com.finance.core.repository;

import com.finance.core.domain.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {
    Page<AuditLogEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLogEntry> findByRequestIdOrderByCreatedAtDesc(String requestId);
}
