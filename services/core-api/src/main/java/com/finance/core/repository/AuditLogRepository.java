package com.finance.core.repository;

import com.finance.core.domain.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {
}
