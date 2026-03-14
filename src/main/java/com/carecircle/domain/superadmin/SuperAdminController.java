package com.carecircle.domain.superadmin;

import com.carecircle.domain.superadmin.dto.SuperAdminDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// =============================================================================
// SUPER_ADMIN PANEL — every endpoint is guarded by BOTH:
//   1. SecurityConfig: .requestMatchers("/api/v1/superadmin/**").hasRole("SUPER_ADMIN")
//   2. @PreAuthorize here: second line of defence — belt AND braces
//
// Endpoints:
//   GET  /api/v1/superadmin/organizations              — all orgs (paginated)
//   GET  /api/v1/superadmin/organizations/{id}         — one org in detail
//   GET  /api/v1/superadmin/users                      — all users (paginated)
//   DELETE /api/v1/superadmin/organizations/{id}       — deactivate org
//   DELETE /api/v1/superadmin/users/{id}               — deactivate any user
// =============================================================================

@RestController
@RequestMapping("/api/v1/superadmin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    // ── GET /superadmin/organizations ─────────────────────────────────────────
    @GetMapping("/organizations")
    public ResponseEntity<Page<SuperAdminDto.OrgSummary>> listOrgs(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(superAdminService.listAllOrgs(pageable));
    }

    // ── GET /superadmin/organizations/{id} ────────────────────────────────────
    @GetMapping("/organizations/{id}")
    public ResponseEntity<SuperAdminDto.OrgDetail> getOrg(@PathVariable UUID id) {
        return ResponseEntity.ok(superAdminService.getOrgDetail(id));
    }

    // ── GET /superadmin/users ─────────────────────────────────────────────────
    @GetMapping("/users")
    public ResponseEntity<Page<SuperAdminDto.UserSummary>> listUsers(
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(superAdminService.listAllUsers(pageable));
    }

    // ── DELETE /superadmin/organizations/{id} ─────────────────────────────────
    // Deactivates the entire org and all its members. Data is preserved.
    @DeleteMapping("/organizations/{id}")
    public ResponseEntity<Void> deactivateOrg(@PathVariable UUID id) {
        superAdminService.deactivateOrg(id);
        return ResponseEntity.noContent().build(); // 204
    }

    // ── DELETE /superadmin/users/{id} ─────────────────────────────────────────
    // Deactivate any individual user across any org.
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID id) {
        superAdminService.deactivateUser(id);
        return ResponseEntity.noContent().build(); // 204
    }
}