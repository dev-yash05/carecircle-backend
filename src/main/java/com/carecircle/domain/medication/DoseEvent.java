package com.carecircle.domain.medication;

import com.carecircle.domain.patient.Patient;
import com.carecircle.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "dose_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_schedule_time",
                columnNames = {"schedule_id", "scheduled_at"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DoseEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private MedicationSchedule schedule;

    // 🧠 Denormalized patient_id: Yes, we can get patient via
    // schedule.getPatient(), but having it directly on DoseEvent
    // lets us query "all pending doses for patient X" with a single
    // indexed lookup. This is a conscious performance trade-off.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DoseStatus status = DoseStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actioned_by")
    private User actionedBy;

    @Column(name = "actioned_at")
    private Instant actionedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // 🧠 OPTIMISTIC LOCKING — The most important field in this entity.
    //
    // The Problem (without this):
    //   - Ramesh (caregiver) opens the app → sees dose is PENDING
    //   - Anjali (admin) also marks it from her phone at the same time
    //   - Both read version=0, both update to TAKEN, last write wins
    //   - The audit log shows TWO "TAKEN" entries — data corruption
    //
    // The Solution (with @Version):
    //   - Ramesh reads: version=0, status=PENDING
    //   - Anjali reads: version=0, status=PENDING
    //   - Ramesh saves first: version becomes 1
    //   - Anjali tries to save: her WHERE clause says version=0
    //   - Postgres finds version=1, not 0 → UPDATE affects 0 rows
    //   - Hibernate throws ObjectOptimisticLockingFailureException
    //   - Your service catches it and returns a 409 CONFLICT to Anjali
    //
    // This is FREE concurrency control — no locks, no performance cost.
    @Version
    @Column(nullable = false)
    private Integer version = 0;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum DoseStatus {
        PENDING, TAKEN, MISSED, SKIPPED
    }
}