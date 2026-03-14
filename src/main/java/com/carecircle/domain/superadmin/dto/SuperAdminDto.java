package com.carecircle.domain.superadmin.dto;

import com.carecircle.domain.organization.Organization;
import com.carecircle.domain.user.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// =============================================================================
// DTOs for the SUPER_ADMIN panel — /api/v1/superadmin/**
//
// These are intentionally verbose. SUPER_ADMIN is you — you want full context.
// =============================================================================

public class SuperAdminDto {

    // ── ORG SUMMARY (used in the paginated list view) ─────────────────────────
    public record OrgSummary(
            UUID                    id,
            String                  name,
            Organization.PlanType   planType,
            int                     memberCount,
            int                     patientCount,
            Instant                 createdAt
    ) {}

    // ── ORG DETAIL (used in the drill-down view for one org) ─────────────────
    public record OrgDetail(
            UUID                    id,
            String                  name,
            Organization.PlanType   planType,
            Instant                 createdAt,
            List<MemberSummary>     members,
            int                     patientCount,
            int                     totalDosesMarked  // TAKEN + SKIPPED combined
    ) {}

    // ── USER SUMMARY (used in the global user list) ───────────────────────────
    public record UserSummary(
            UUID        id,
            String      email,
            String      fullName,
            User.Role   role,
            String      organizationName,  // null for SUPER_ADMIN
            UUID        organizationId,    // null for SUPER_ADMIN
            boolean     isActive,
            boolean     hasLoggedIn,       // googleSubjectId != null
            Instant     createdAt
    ) {}

    // ── MEMBER SUMMARY (nested inside OrgDetail) ──────────────────────────────
    public record MemberSummary(
            UUID        id,
            String      email,
            String      fullName,
            User.Role   role,
            boolean     isActive,
            boolean     hasLoggedIn,
            Instant     createdAt
    ) {}
}