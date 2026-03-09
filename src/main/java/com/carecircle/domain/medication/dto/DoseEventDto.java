package com.carecircle.domain.medication.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

public class DoseEventDto {

    @Getter @Setter @NoArgsConstructor
    public static class MarkRequest {
        @NotNull(message = "Status is required")
        private String status;   // TAKEN | SKIPPED
        private String notes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private UUID id;
        private UUID patientId;
        private String patientName;
        private String medicationName;
        private String dosage;
        private Instant scheduledAt;
        private String status;
        private String actionedByName;
        private Instant actionedAt;
        private String notes;
        private Integer version;  // For optimistic locking awareness
    }
}