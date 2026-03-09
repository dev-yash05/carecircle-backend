package com.carecircle.domain.medication;

import com.carecircle.domain.medication.dto.DoseEventDto;
import com.carecircle.domain.medication.dto.MedicationDto;
import com.carecircle.domain.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    // POST /api/v1/organizations/{orgId}/medications
    // 🧠 @PreAuthorize: Only ADMIN can create medication schedules.
    // CAREGIVER can only mark doses — not create schedules.
    // This is RBAC in action.
    @PostMapping("/medications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public MedicationDto.Response createSchedule(
            @PathVariable UUID orgId,
            @Valid @RequestBody MedicationDto.CreateRequest request,
            @AuthenticationPrincipal User currentUser
            // 🧠 @AuthenticationPrincipal: Spring injects the currently
            // logged-in user from the SecurityContext. This is what
            // JwtAuthFilter set when it validated the cookie.
    ) {
        return medicationService.createSchedule(orgId, request, currentUser);
    }

    // GET /api/v1/organizations/{orgId}/patients/{patientId}/medications
    @GetMapping("/patients/{patientId}/medications")
    public List<MedicationDto.Response> getSchedules(
            @PathVariable UUID orgId,
            @PathVariable UUID patientId
    ) {
        return medicationService.getSchedules(orgId, patientId);
    }

    // GET /api/v1/organizations/{orgId}/patients/{patientId}/doses
    @GetMapping("/patients/{patientId}/doses")
    public Page<DoseEventDto.Response> getDoseEvents(
            @PathVariable UUID orgId,
            @PathVariable UUID patientId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return medicationService.getDoseEvents(orgId, patientId, pageable);
    }

    // PUT /api/v1/organizations/{orgId}/doses/{doseEventId}/mark
    @PutMapping("/doses/{doseEventId}/mark")
    public DoseEventDto.Response markDose(
            @PathVariable UUID orgId,
            @PathVariable UUID doseEventId,
            @Valid @RequestBody DoseEventDto.MarkRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return medicationService.markDose(doseEventId, request, currentUser);
    }
}