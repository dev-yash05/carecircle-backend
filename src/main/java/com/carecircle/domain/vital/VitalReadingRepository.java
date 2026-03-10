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

    // Time-series for a specific vital type — used for charting + GET API
    Page<VitalReading> findByPatientIdAndVitalTypeOrderByMeasuredAtDesc(
            UUID patientId, VitalReading.VitalType vitalType, Pageable pageable);

    // All readings in a time window — used by PDF report generator
    @Query("SELECT v FROM VitalReading v WHERE v.patient.id = :patientId " +
            "AND v.measuredAt BETWEEN :from AND :to ORDER BY v.measuredAt DESC")
    List<VitalReading> findByPatientIdInWindow(
            @Param("patientId") UUID patientId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}