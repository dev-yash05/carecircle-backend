package com.carecircle.domain.medication;

import com.carecircle.domain.medication.dto.DoseEventDto;
import com.carecircle.domain.medication.dto.MedicationDto;
import com.carecircle.domain.outbox.OutboxEvent;
import com.carecircle.domain.outbox.OutboxEventRepository;
import com.carecircle.domain.patient.Patient;
import com.carecircle.domain.patient.PatientRepository;
import com.carecircle.domain.user.User;
import com.carecircle.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationService {

    private final MedicationScheduleRepository scheduleRepository;
    private final DoseEventRepository doseEventRepository;
    private final PatientRepository patientRepository;
    private final OutboxEventRepository outboxEventRepository;

    // -------------------------------------------------------------------------
    // CREATE MEDICATION SCHEDULE
    // -------------------------------------------------------------------------
    @Transactional
    public MedicationDto.Response createSchedule(UUID orgId, MedicationDto.CreateRequest request,
                                                 User createdBy) {
        Patient patient = patientRepository
                .findByIdAndOrganizationId(request.getPatientId(), orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", request.getPatientId()));

        MedicationSchedule schedule = new MedicationSchedule();
        schedule.setPatient(patient);
        schedule.setCreatedBy(createdBy);
        schedule.setMedicationName(request.getMedicationName());
        schedule.setDosage(request.getDosage());
        schedule.setInstructions(request.getInstructions());
        schedule.setCronExpression(request.getCronExpression());
        schedule.setTimezone(request.getTimezone());
        schedule.setStartDate(request.getStartDate());
        schedule.setEndDate(request.getEndDate());
        schedule.setActive(true);

        MedicationSchedule saved = scheduleRepository.save(schedule);
        log.info("Medication schedule created: {} for patient: {}",
                saved.getId(), patient.getId());

        return toScheduleResponse(saved);
    }

    // -------------------------------------------------------------------------
    // GET SCHEDULES FOR PATIENT
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public java.util.List<MedicationDto.Response> getSchedules(UUID orgId, UUID patientId) {
        patientRepository.findByIdAndOrganizationId(patientId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        return scheduleRepository
                .findByPatientIdAndActive(patientId, true)
                .stream()
                .map(this::toScheduleResponse)
                .toList();
    }

    // -------------------------------------------------------------------------
    // MARK DOSE AS TAKEN / SKIPPED
    // -------------------------------------------------------------------------
    // 🧠 THIS IS THE MOST IMPORTANT METHOD — IT DEMONSTRATES:
    // 1. Optimistic Locking (handles concurrent updates)
    // 2. Transactional Outbox Pattern (guaranteed notification delivery)
    // 3. @Transactional (atomic — both updates succeed or both roll back)
    @Transactional
    public DoseEventDto.Response markDose(UUID doseEventId, DoseEventDto.MarkRequest request,
                                          User actionedBy) {
        // 1. Fetch the dose event
        DoseEvent dose = doseEventRepository.findById(doseEventId)
                .orElseThrow(() -> new ResourceNotFoundException("DoseEvent", doseEventId));

        // 2. Validate — can't mark a dose that's already been actioned
        if (dose.getStatus() != DoseEvent.DoseStatus.PENDING) {
            throw new IllegalStateException(
                    "Dose is already " + dose.getStatus() + ". Cannot update.");
        }

        // 3. Validate the new status
        DoseEvent.DoseStatus newStatus;
        try {
            newStatus = DoseEvent.DoseStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + request.getStatus());
        }

        if (newStatus == DoseEvent.DoseStatus.PENDING || newStatus == DoseEvent.DoseStatus.MISSED) {
            throw new IllegalArgumentException("Can only mark as TAKEN or SKIPPED");
        }

        // 4. Update dose status
        dose.setStatus(newStatus);
        dose.setActionedBy(actionedBy);
        dose.setActionedAt(Instant.now());
        dose.setNotes(request.getNotes());

        // 5. Save — Hibernate increments @Version automatically
        // If another request already updated this dose, Hibernate throws
        // ObjectOptimisticLockingFailureException → caught below
        DoseEvent saved;
        try {
            saved = doseEventRepository.save(dose);
        } catch (ObjectOptimisticLockingFailureException e) {
            // 🧠 TWO caregivers tried to mark the same dose simultaneously.
            // The first one wins. The second gets a 409 Conflict.
            throw new IllegalStateException(
                    "This dose was already updated by another user. Please refresh.");
        }

        // 6. TRANSACTIONAL OUTBOX PATTERN
        // In the SAME transaction: insert an outbox event.
        // If this transaction commits → both the dose update AND the outbox
        // event are saved atomically. No notification can be lost.
        OutboxEvent outboxEvent = OutboxEvent.of(
                "DoseEvent",
                saved.getId(),
                newStatus == DoseEvent.DoseStatus.TAKEN ? "DOSE_TAKEN" : "DOSE_SKIPPED",
                Map.of(
                        "doseEventId", saved.getId().toString(),
                        "patientId", saved.getPatient().getId().toString(),
                        "patientName", saved.getPatient().getFullName(),
                        "medicationName", saved.getSchedule().getMedicationName(),
                        "actionedBy", actionedBy.getFullName(),
                        "actionedAt", saved.getActionedAt().toString(),
                        "status", newStatus.name()
                )
        );
        outboxEventRepository.save(outboxEvent);

        log.info("Dose {} marked as {} by user {}",
                doseEventId, newStatus, actionedBy.getEmail());

        return toDoseResponse(saved);
    }

    // -------------------------------------------------------------------------
    // GET DOSE EVENTS FOR PATIENT (paginated)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public Page<DoseEventDto.Response> getDoseEvents(UUID orgId, UUID patientId, Pageable pageable) {
        patientRepository.findByIdAndOrganizationId(patientId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        return doseEventRepository
                .findByPatientIdOrderByScheduledAtDesc(patientId, pageable)
                .map(this::toDoseResponse);
    }

    // -------------------------------------------------------------------------
    // MAPPERS (inline — no MapStruct needed for these since logic is custom)
    // -------------------------------------------------------------------------
    private MedicationDto.Response toScheduleResponse(MedicationSchedule s) {
        return MedicationDto.Response.builder()
                .id(s.getId())
                .patientId(s.getPatient().getId())
                .patientName(s.getPatient().getFullName())
                .medicationName(s.getMedicationName())
                .dosage(s.getDosage())
                .instructions(s.getInstructions())
                .cronExpression(s.getCronExpression())
                .timezone(s.getTimezone())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .active(s.isActive())
                .createdAt(s.getCreatedAt())
                .build();
    }

    private DoseEventDto.Response toDoseResponse(DoseEvent d) {
        return DoseEventDto.Response.builder()
                .id(d.getId())
                .patientId(d.getPatient().getId())
                .patientName(d.getPatient().getFullName())
                .medicationName(d.getSchedule().getMedicationName())
                .dosage(d.getSchedule().getDosage())
                .scheduledAt(d.getScheduledAt())
                .status(d.getStatus().name())
                .actionedByName(d.getActionedBy() != null ? d.getActionedBy().getFullName() : null)
                .actionedAt(d.getActionedAt())
                .notes(d.getNotes())
                .version(d.getVersion())
                .build();
    }
}