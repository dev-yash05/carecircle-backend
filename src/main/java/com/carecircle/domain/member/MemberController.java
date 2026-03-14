package com.carecircle.domain.member;

import com.carecircle.domain.member.dto.MemberDto;
import com.carecircle.domain.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// =============================================================================
// MEMBER MANAGEMENT ENDPOINTS
//
// POST   /api/v1/organizations/{orgId}/members          — add member (pre-register)
// GET    /api/v1/organizations/{orgId}/members          — list all members
// DELETE /api/v1/organizations/{orgId}/members/{userId} — deactivate member
//
// Access:
//   ADMIN       — can only manage their own org
//   SUPER_ADMIN — can manage any org
//   CAREGIVER / VIEWER — blocked (SecurityConfig + @PreAuthorize)
// =============================================================================

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    // ── POST /organizations/{orgId}/members ───────────────────────────────────
    // Admin adds a new caregiver or viewer to their org.
    // No email is sent. Admin tells them verbally / via WhatsApp to log in.
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<MemberDto.Response> addMember(
            @PathVariable UUID orgId,
            @Valid @RequestBody MemberDto.CreateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        MemberDto.Response response = memberService.addMember(orgId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /organizations/{orgId}/members ────────────────────────────────────
    // Returns all active members — useful for the Admin's "Team" screen
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<MemberDto.Response>> listMembers(
            @PathVariable UUID orgId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(memberService.listMembers(orgId, currentUser));
    }

    // ── DELETE /organizations/{orgId}/members/{userId} ────────────────────────
    // Soft-deactivates the member (sets is_active = false).
    // Their historical data (doses marked, vitals recorded) is preserved.
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID orgId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal User currentUser
    ) {
        memberService.removeMember(orgId, userId, currentUser);
        return ResponseEntity.noContent().build(); // 204
    }
}