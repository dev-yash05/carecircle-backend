package com.carecircle.domain.vital.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class VitalDto {

    @Getter @Setter @NoArgsConstructor
    public static class CreateRequest {

        @NotNull(message = "vitalType is required")
        private String vitalType;

        // JSONB — shape depends on vitalType:
        //   BLOOD_PRESSURE: { "systolic": 130, "diastolic": 85 }
        //   BLOOD_SUGAR:    { "value": 110, "unit": "mg/dL" }
        @NotNull(message = "readingValue is required")
        private Map<String, Object> readingValue;

        // Optional — defaults to Instant.now() in service if not provided
        private Instant measuredAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {
        private UUID id;
        private UUID patientId;
        private String patientName;
        private String vitalType;
        private Map<String, Object> readingValue;
        private Instant measuredAt;
        private boolean anomalous;
        private boolean alertTriggered;
        private String recordedByName;
        private Instant createdAt;
    }
}