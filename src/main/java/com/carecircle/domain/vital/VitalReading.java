package com.carecircle.domain.vital;

import com.carecircle.domain.patient.Patient;
import com.carecircle.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// =============================================================================
// 🧠 VITAL READINGS ENTITY
//
// This table already exists in V1__init_schema.sql — no new migration needed.
// The schema uses JSONB for reading_value which handles:
//   BLOOD_PRESSURE: { "systolic": 130, "diastolic": 85, "unit": "mmHg" }
//   BLOOD_SUGAR:    { "value": 110, "unit": "mg/dL" }
//   WEIGHT:         { "value": 72.5, "unit": "kg" }
//   SPO2:           { "value": 98, "unit": "%" }
//   TEMPERATURE:    { "value": 37.2, "unit": "C" }
//
// 🧠 Why JSONB instead of separate columns?
//   Different vital types have completely different structures.
//   One table with JSONB beats 5 tables or 10 nullable columns.
//   The reading is still queryable: reading_value->>'systolic' > '160'
// =============================================================================

@Entity
@Table(name = "vital_readings")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class VitalReading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private User recordedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "vital_type", nullable = false, length = 50)
    private VitalType vitalType;

    // Flexible JSONB — structure depends on vitalType
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reading_value", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> readingValue;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    // Set to true by anomaly detection logic in VitalService
    @Column(name = "is_anomalous", nullable = false)
    @Builder.Default
    private boolean anomalous = false;

    // Set to true after anomaly alert has been dispatched via Outbox
    @Column(name = "alert_triggered", nullable = false)
    @Builder.Default
    private boolean alertTriggered = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    // -------------------------------------------------------------------------
    // VITAL TYPES — must match DB CHECK constraint in V1 migration
    // -------------------------------------------------------------------------
    public enum VitalType {
        BLOOD_PRESSURE,
        BLOOD_SUGAR,
        WEIGHT,
        SPO2,
        TEMPERATURE
    }
}