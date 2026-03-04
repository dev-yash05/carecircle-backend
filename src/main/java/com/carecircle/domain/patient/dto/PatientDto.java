package com.carecircle.domain.patient.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// =============================================================================
// 🧠 WHY DTOs? (Data Transfer Objects)
//
// NEVER expose your JPA Entity directly in your API response.
// Reason 1 — Security: Your Patient entity has organization_id,
//   internal flags, etc. You don't want to expose all of that.
// Reason 2 — Stability: If you rename a DB column, your API contract
//   breaks for every frontend/client using it.
// Reason 3 — Circular references: Entity relationships cause infinite
//   JSON serialization loops (Patient → Organization → Patient...).
//
// DTOs are the "contract" between your API and the outside world.
// Entities are the "contract" between your code and the database.
// They are different things and should stay separate.
// =============================================================================

public class PatientDto {

    // -------------------------------------------------------------------------
    // REQUEST DTO — What the frontend sends TO your API (POST/PUT)
    // -------------------------------------------------------------------------
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {

        @NotBlank(message = "Patient name is required")
        @Size(max = 255, message = "Name cannot exceed 255 characters")
        private String fullName;

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        private LocalDate dateOfBirth;

        @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Gender must be MALE, FEMALE, or OTHER")
        private String gender;

        @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Invalid blood type format (e.g. A+, O-)")
        private String bloodType;

        // Flexible metadata — frontend sends whatever extra info is needed
        private Map<String, Object> metadata;
    }

    // -------------------------------------------------------------------------
    // UPDATE REQUEST — Separate from Create because some fields are immutable
    // (you can't change a patient's date of birth after creation)
    // -------------------------------------------------------------------------
    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateRequest {

        @NotBlank(message = "Patient name is required")
        @Size(max = 255)
        private String fullName;

        private String gender;

        @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Invalid blood type format")
        private String bloodType;

        private Map<String, Object> metadata;
    }

    // -------------------------------------------------------------------------
    // RESPONSE DTO — What your API sends BACK to the frontend (GET)
    // -------------------------------------------------------------------------
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private UUID id;
        private String fullName;
        private LocalDate dateOfBirth;
        private String gender;
        private String bloodType;
        private Map<String, Object> metadata;
        private boolean isActive;

        // 🧠 We expose organization NAME, not the internal UUID.
        // Frontend shows "Sharma Family Circle", not a raw UUID.
        private String organizationName;

        private Instant createdAt;
        private Instant updatedAt;
    }

    // -------------------------------------------------------------------------
    // SUMMARY DTO — Lightweight version for list views
    // 🧠 When listing 50 patients, you don't need full metadata.
    // Return only what the list UI actually needs. Less data = faster.
    // -------------------------------------------------------------------------
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Summary {
        private UUID id;
        private String fullName;
        private LocalDate dateOfBirth;
        private String gender;
        private boolean isActive;
    }
}