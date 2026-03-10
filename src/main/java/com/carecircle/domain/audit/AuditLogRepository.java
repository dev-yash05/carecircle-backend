package com.carecircle.domain.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // All audit entries for a patient, newest first — used by the API
    Page<AuditLog> findByEntityIdOrderByCreatedAtDesc(UUID entityId, Pageable pageable);

    // All entries for an org — for org-level audit reports (future)
    Page<AuditLog> findByOrganizationIdOrderByCreatedAtDesc(UUID orgId, Pageable pageable);
}