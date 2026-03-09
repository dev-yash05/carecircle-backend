package com.carecircle.domain.medication.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class MedicationDto {

    @Getter @Setter @NoArgsConstructor
    public static class CreateRequest {

        @NotNull(message = "Patient ID is required")
        private UUID patientId;

        @NotBlank(message = "Medication name is required")
        private String medicationName;

        @NotBlank(message = "Dosage is required")
        private String dosage;

        private String instructions;

        // 🧠 CRON format: "0 8,20 * * ?" = 8AM and 8PM every day
        // "0 9 * * MON-FRI" = 9AM on weekdays only
        // We validate it's not blank — deeper CRON validation in service
        @NotBlank(message = "CRON expression is required")
        private String cronExpression;

        private String timezone = "Asia/Kolkata";

        @NotNull(message = "Start date is required")
        @FutureOrPresent(message = "Start date must be today or in the future")
        private LocalDate startDate;

        private LocalDate endDate;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private UUID id;
        private UUID patientId;
        private String patientName;
        private String medicationName;
        private String dosage;
        private String instructions;
        private String cronExpression;
        private String timezone;
        private LocalDate startDate;
        private LocalDate endDate;
        private boolean active;
        private Instant createdAt;
    }
}