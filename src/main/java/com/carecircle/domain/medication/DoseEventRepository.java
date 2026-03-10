package com.carecircle.domain.medication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// =============================================================================
// 🧠 WHY THE PREVIOUS VERSION FAILED:
//
// Spring Data parses method names left-to-right to derive queries.
// "findByPatientIdWithAssociations" was parsed as:
//   findBy → PatientId → WithAssociations (property lookup on UUID → FAIL)
//
// @EntityGraph does NOT change how Spring Data parses the method name.
// It only affects HOW Hibernate fetches the result — it does not add new
// method name segments you can invent.
//
// THE FIX — two valid approaches:
//
//   Option A: Override an existing derived-name method with @EntityGraph.
//     Spring Data lets you redeclare a derived query and add @EntityGraph.
//     The method name must still follow the standard naming convention.
//     findById() and findByPatientIdOrderByScheduledAtDesc() already exist
//     (inherited or declared) — we just add @EntityGraph to them.
//
//   Option B: Use @Query with JPQL and @EntityGraph.
//     The @Query provides the SQL, @EntityGraph controls join fetch strategy.
//
// We use Option A for findById (override inherited) and Option A for the
// paginated query (it was already declared — just add @EntityGraph).
// =============================================================================

@Repository
public interface DoseEventRepository extends JpaRepository<DoseEvent, UUID> {

    // -------------------------------------------------------------------------
    // ✅ N+1 FIX: @EntityGraph on VALID derived query method names
    // -------------------------------------------------------------------------

    // Override JpaRepository.findById() to add @EntityGraph.
    // Spring Data allows overriding inherited methods — @EntityGraph applies.
    // Used by markDose() to load schedule + patient + actionedBy in one JOIN.
    @Override
    @EntityGraph(attributePaths = {"schedule", "patient", "actionedBy"})
    Optional<DoseEvent> findById(UUID id);

    // The paginated query was already declared below — just add @EntityGraph.
    // Spring Data parses: findBy → PatientId → OrderBy → ScheduledAt → Desc ✓
    // @EntityGraph fetches all 3 associations in the same JOIN query.
    @EntityGraph(attributePaths = {"schedule", "patient", "actionedBy"})
    Page<DoseEvent> findByPatientIdOrderByScheduledAtDesc(UUID patientId, Pageable pageable);

    // -------------------------------------------------------------------------
    // EXISTING queries (unchanged from Sprint 4)
    // -------------------------------------------------------------------------

    @Query("SELECT d FROM DoseEvent d WHERE d.patient.id = :patientId " +
            "AND d.status = 'PENDING' " +
            "AND d.scheduledAt BETWEEN :from AND :to")
    List<DoseEvent> findPendingDosesInWindow(
            @Param("patientId") UUID patientId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    boolean existsByScheduleIdAndScheduledAt(UUID scheduleId, Instant scheduledAt);

    @Query("SELECT d FROM DoseEvent d WHERE d.status = 'PENDING' " +
            "AND d.scheduledAt <= :cutoff")
    List<DoseEvent> findOverduePendingDoses(@Param("cutoff") Instant cutoff);
}