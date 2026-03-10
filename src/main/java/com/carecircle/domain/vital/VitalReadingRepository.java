package com.carecircle.domain.vital;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface VitalReadingRepository extends JpaRepository<VitalReading, UUID> {

    // Time-series: all readings of one type for a patient, newest first
    // Used for charting trends (e.g. BP over last 30 days)
    Page<VitalReading> findByPatientIdAndVitalTypeOrderByMeasuredAtDesc(
            UUID patientId,
            VitalReading.VitalType vitalType,
            Pageable pageable
    );

    // All readings for a patient in a time window — used for PDF report generation (Sprint 6)
    @Query("SELECT v FROM VitalReading v WHERE v.patient.id = :patientId " +
            "AND v.measuredAt BETWEEN :from AND :to " +
            "ORDER BY v.measuredAt DESC")
    List<VitalReading> findByPatientIdInWindow(
            @Param("patientId") UUID patientId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    // Anomalous readings that haven't triggered an alert yet
    // Polled by a background job in case outbox processing was delayed
    List<VitalReading> findByPatientIdAndAnomalousAndAlertTriggered(
            UUID patientId,
            boolean anomalous,
            boolean alertTriggered
    );
}