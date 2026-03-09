package com.carecircle.domain.medication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoseEventRepository extends JpaRepository<DoseEvent, UUID> {

    // "What doses are pending for patient X in the next 2 hours?"
    // Used by the notification module
    @Query("SELECT d FROM DoseEvent d WHERE d.patient.id = :patientId " +
            "AND d.status = 'PENDING' " +
            "AND d.scheduledAt BETWEEN :from AND :to")
    List<DoseEvent> findPendingDosesInWindow(
            @Param("patientId") UUID patientId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // "Has this exact dose already been generated?" (prevents duplicates)
    // Called by DoseEventScheduler before inserting
    boolean existsByScheduleIdAndScheduledAt(UUID scheduleId, Instant scheduledAt);

    // "Show me all doses for patient X" — for the daily dashboard view
    // 🧠 This result is cached in Redis — see Phase 5
    Page<DoseEvent> findByPatientIdOrderByScheduledAtDesc(UUID patientId, Pageable pageable);

    // "Show me all PENDING doses across ALL patients" — for the scheduler
    @Query("SELECT d FROM DoseEvent d WHERE d.status = 'PENDING' " +
            "AND d.scheduledAt <= :cutoff")
    List<DoseEvent> findOverduePendingDoses(@Param("cutoff") Instant cutoff);
}