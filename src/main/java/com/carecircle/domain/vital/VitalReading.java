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

@Entity
@Table(name = "vital_readings")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reading_value", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> readingValue;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "is_anomalous", nullable = false)
    @Builder.Default
    private boolean anomalous = false;

    @Column(name = "alert_triggered", nullable = false)
    @Builder.Default
    private boolean alertTriggered = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public enum VitalType {
        BLOOD_PRESSURE, BLOOD_SUGAR, WEIGHT, SPO2, TEMPERATURE
    }
}