package com.carecircle.domain.member.dto;

import com.carecircle.domain.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

// =============================================================================
// 🧠 DTOs for POST /organizations/{orgId}/members
//
// CreateRequest  — what the Admin sends when adding a Caregiver or Viewer
// Response       — what the API sends back (and what the member list returns)
//
// We use Java Records here (introduced in Java 16) for immutable DTOs.
// They are perfect for request/response objects — no boilerplate, no setters.
// =============================================================================

public class MemberDto {

    // ── CREATE REQUEST ────────────────────────────────────────────────────────
    public record CreateRequest(

            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            String email,

            // 🧠 Only CAREGIVER or VIEWER can be added by an Admin.
            // An Admin cannot promote someone to ADMIN or SUPER_ADMIN via this endpoint.
            // Validation is enforced in MemberService, not just here.
            @NotNull(message = "Role is required")
            User.Role role
    ) {}

    // ── RESPONSE ─────────────────────────────────────────────────────────────
    public record Response(
            UUID    id,
            String  email,
            String  fullName,       // null until they first log in
            String  avatarUrl,      // null until they first log in
            User.Role role,
            boolean isActive,
            boolean hasLoggedIn,    // true if googleSubjectId is not null
            Instant createdAt
    ) {}
}