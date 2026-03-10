package com.carecircle.domain.vital.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class VitalDto {

    // -------------------------------------------------------------------------
    // CREATE REQUEST — What the caregiver POSTs
    // -------------------------------------------------------------------------
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {

        @NotNull(message = "Vital type is required")
        private String vitalType;   // BLOOD_PRESSURE | BLOOD_SUGAR | WEIGHT | SPO2 | TEMPERATURE

        // 🧠 JSONB value — the shape depends on vitalType:
        //   BLOOD_PRESSURE: { "systolic": 130, "diastolic": 85 }
        //   BLOOD_SUGAR:    { "value": 110, "unit": "mg/dL" }
        //   WEIGHT:         { "value": 72.5, "unit": "kg" }
        @NotNull(message = "Reading value is required")
        private Map<String, Object> readingValue;

        // When was it actually measured? Defaults to now if not provided.
        // Allows backdating a reading (e.g. caregiver logs it 10 min later)
        private Instant measuredAt;
    }

    // -------------------------------------------------------------------------
    // RESPONSE — What the API returns
    // -------------------------------------------------------------------------
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
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