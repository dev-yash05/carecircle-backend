package com.carecircle.domain.patient;

import com.carecircle.domain.organization.Organization;
import com.carecircle.domain.organization.OrganizationRepository;
import com.carecircle.domain.patient.dto.PatientDto;
import com.carecircle.domain.patient.mapper.PatientMapper;
import com.carecircle.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// =============================================================================
// 🧠 SERVICE LAYER RULES (Senior Principles):
//
// 1. ALL business logic lives here. Controllers are dumb — they only
//    handle HTTP concerns (parsing request, returning response).
//
// 2. @Transactional: The entire method runs in ONE database transaction.
//    If anything fails mid-way, the entire operation rolls back.
//    Without this, a failure after save() but before the audit log
//    would leave your data in a corrupted half-state.
//
// 3. @Transactional(readOnly = true) on GET methods:
//    Tells Hibernate "no writes will happen here." Hibernate skips
//    dirty checking (comparing entity state before/after), which
//    makes read operations significantly faster.
// =============================================================================

@Slf4j                        // Gives you: log.info(), log.error(), log.debug()
@Service
@RequiredArgsConstructor      // Lombok generates constructor with all final fields (= constructor injection)
public class PatientService {

    private final PatientRepository patientRepository;
    private final OrganizationRepository organizationRepository;
    private final PatientMapper patientMapper;

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------
    @Transactional
    public PatientDto.Response createPatient(UUID organizationId, PatientDto.CreateRequest request) {
        log.info("Creating patient for org: {}", organizationId);

        // 1. Verify the organization exists
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));

        // 2. Convert DTO → Entity (MapStruct handles the field mapping)
        Patient patient = patientMapper.toEntity(request);

        // 3. Set the fields MapStruct ignored (security-sensitive fields)
        patient.setOrganization(org);

        // 4. Persist — Hibernate generates the UUID and sets createdAt/updatedAt
        Patient saved = patientRepository.save(patient);

        log.info("Patient created with id: {}", saved.getId());

        // 5. Convert Entity → Response DTO and return
        return patientMapper.toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // GET ALL (Paginated)
    // -------------------------------------------------------------------------
    // 🧠 readOnly = true: Faster reads, no dirty-checking overhead
    @Transactional(readOnly = true)
    public Page<PatientDto.Summary> getPatients(UUID organizationId, Pageable pageable) {
        // 🧠 We return Page<Summary> not Page<Response> for list views.
        // Summary is lighter — no metadata, no orgName.
        // The frontend list screen doesn't need that data.
        return patientRepository
                .findByOrganizationIdAndActive(organizationId, true, pageable)
                .map(patientMapper::toSummary);   // map() transforms each entity in the page
    }

    // -------------------------------------------------------------------------
    // GET ONE
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PatientDto.Response getPatient(UUID organizationId, UUID patientId) {
        // 🧠 findByIdAndOrganizationId: This is the security check.
        // A user from Org A cannot access a patient from Org B,
        // even if they know the UUID. The query enforces the tenant boundary.
        Patient patient = patientRepository
                .findByIdAndOrganizationId(patientId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        return patientMapper.toResponse(patient);
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------
    @Transactional
    public PatientDto.Response updatePatient(UUID organizationId, UUID patientId,
                                             PatientDto.UpdateRequest request) {
        // 1. Fetch with tenant check
        Patient patient = patientRepository
                .findByIdAndOrganizationId(patientId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        // 2. MapStruct updates ONLY the non-null fields from request
        //    Fields not included in UpdateRequest are untouched
        patientMapper.updateEntity(request, patient);

        // 3. save() detects the entity is already managed (has an ID),
        //    so Hibernate issues an UPDATE, not an INSERT
        Patient updated = patientRepository.save(patient);

        log.info("Patient {} updated", patientId);
        return patientMapper.toResponse(updated);
    }

    // -------------------------------------------------------------------------
    // SOFT DELETE
    // -------------------------------------------------------------------------
    // 🧠 SOFT DELETE vs HARD DELETE:
    // We NEVER delete patient records. Medical history must be preserved.
    // Instead, we set isActive = false.
    // The UI hides inactive patients. The data stays in the DB.
    // This is also required for audit compliance.
    @Transactional
    public void deactivatePatient(UUID organizationId, UUID patientId) {
        Patient patient = patientRepository
                .findByIdAndOrganizationId(patientId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        patient.setActive(false);
        patientRepository.save(patient);

        log.info("Patient {} deactivated (soft delete)", patientId);
    }
}