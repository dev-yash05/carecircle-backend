package com.carecircle.domain.medication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, UUID> {

    List<MedicationSchedule> findByPatientIdAndActive(UUID patientId, boolean isActive);

    // 🧠 This query is called by the Quartz Scheduler every minute.
    // It finds ALL active schedules across ALL patients.
    // The scheduler then generates DoseEvents for the next 24 hours.
    @Query("SELECT m FROM MedicationSchedule m WHERE m.active = true " +
            "AND m.startDate <= CURRENT_DATE " +
            "AND (m.endDate IS NULL OR m.endDate >= CURRENT_DATE)")
    List<MedicationSchedule> findAllCurrentlyActive();
}
