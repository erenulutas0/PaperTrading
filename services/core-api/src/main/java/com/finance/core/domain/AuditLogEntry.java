package com.finance.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_actor_created_desc", columnList = "actor_id, created_at"),
        @Index(name = "idx_audit_logs_resource_created_desc", columnList = "resource_type, resource_id, created_at"),
        @Index(name = "idx_audit_logs_request_id", columnList = "request_id"),
        @Index(name = "idx_audit_logs_created_desc", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 100)
    private AuditActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 100)
    private AuditResourceType resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "ip_address", length = 255)
    private String ipAddress;

    @Column(name = "user_agent", length = 1024)
    private String userAgent;

    @Column(name = "request_method", length = 16)
    private String requestMethod;

    @Column(name = "request_path", length = 255)
    private String requestPath;

    @Column(columnDefinition = "text")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
