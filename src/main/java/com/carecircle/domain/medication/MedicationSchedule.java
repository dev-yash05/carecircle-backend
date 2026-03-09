package com.carecircle.domain.medication;

import com.carecircle.domain.patient.Patient;
import com.carecircle.domain.user.User;
import com.carecircle.shared.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "medication_schedules")
@Getter
@Setter
@NoArgsConstructor
public class MedicationSchedule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @NotBlank
    @Column(name = "medication_name", nullable = false)
    private String medicationName;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String dosage;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    // 🧠 CRON expression: "0 8,20 * * *" = 8AM and 8PM every day
    // Quartz Scheduler will read this to generate DoseEvents.
    // Storing as string gives infinite scheduling flexibility.
    @NotBlank
    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    @Column(nullable = false, length = 100)
    private String timezone = "Asia/Kolkata";

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;  // null = ongoing prescription

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}