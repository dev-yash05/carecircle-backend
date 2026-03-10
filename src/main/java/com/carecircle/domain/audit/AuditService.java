package com.carecircle.domain.audit;

import com.carecircle.domain.audit.dto.AuditLogDto;
import com.carecircle.domain.patient.PatientRepository;
import com.carecircle.domain.user.User;
import com.carecircle.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final PatientRepository patientRepository;

    // -------------------------------------------------------------------------
    // WRITE AUDIT LOG
    // -------------------------------------------------------------------------
    // 🧠 PROPAGATION.REQUIRES_NEW: The audit write runs in its OWN transaction,
    // separate from the caller's transaction.
    //
    // Why? If markDose() fails and rolls back, we still want to know
    // that someone ATTEMPTED the action. Audit logs capture intent and attempts,
    // not just successful outcomes.
    //
    // Also: if the audit write fails (e.g. DB issue), we don't want
    // to roll back the main business operation. Audit is secondary.
    //
    // @Async: The audit write is fire-and-forget. The caregiver's PUT /mark
    // response doesn't wait for the audit row to be written.
    // Keeps API response times fast.
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID orgId, User actor, String action,
                    String entityType, UUID entityId,
                    Map<String, Object> newValue) {
        try {
            AuditLog entry = AuditLog.builder()
                    .organizationId(orgId)
                    .actorId(actor != null ? actor.getId() : null)
                    .actorEmail(actor != null ? actor.getEmail() : "system")
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .newValue(newValue)
                    .createdAt(Instant.now())
                    .build();

            auditLogRepository.save(entry);
            log.debug("Audit: {} by {} on {}:{}", action,
                    actor != null ? actor.getEmail() : "system", entityType, entityId);

        } catch (Exception e) {
            // Never let audit failure propagate — log it and move on
            log.error("Audit log write failed for action={} entity={}:{} — {}",
                    action, entityType, entityId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // GET AUDIT TRAIL FOR A PATIENT
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public Page<AuditLogDto.Response> getPatientAuditLog(UUID orgId, UUID patientId,
                                                         Pageable pageable) {
        // Verify the patient belongs to this org (tenant isolation)
        patientRepository.findByIdAndOrganizationId(patientId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        return auditLogRepository
                .findByEntityIdOrderByCreatedAtDesc(patientId, pageable)
                .map(this::toResponse);
    }

    private AuditLogDto.Response toResponse(AuditLog a) {
        return AuditLogDto.Response.builder()
                .id(a.getId())
                .action(a.getAction())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .actorEmail(a.getActorEmail())
                .newValue(a.getNewValue())
                .oldValue(a.getOldValue())
                .createdAt(a.getCreatedAt())
                .build();
    }
}