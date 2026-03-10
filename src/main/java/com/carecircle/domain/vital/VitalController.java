package com.carecircle.domain.vital;

import com.carecircle.domain.user.User;
import com.carecircle.domain.vital.dto.VitalDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/patients/{patientId}/vitals")
@RequiredArgsConstructor
public class VitalController {

    private final VitalService vitalService;

    // POST /api/v1/organizations/{orgId}/patients/{patientId}/vitals
    // Body: { "vitalType": "BLOOD_PRESSURE", "readingValue": {"systolic": 165, "diastolic": 95} }
    // → If systolic > 160: saves reading + writes BP_ANOMALY_DETECTED outbox event atomically
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VitalDto.Response recordVital(
            @PathVariable UUID orgId,
            @PathVariable UUID patientId,
            @Valid @RequestBody VitalDto.CreateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return vitalService.recordVital(orgId, patientId, request, currentUser);
    }

    // GET /api/v1/organizations/{orgId}/patients/{patientId}/vitals?vitalType=BLOOD_PRESSURE
    // Returns paginated time-series data for charting
    @GetMapping
    public Page<VitalDto.Response> getVitals(
            @PathVariable UUID orgId,
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "BLOOD_PRESSURE") String vitalType,
            @PageableDefault(size = 30) Pageable pageable
    ) {
        return vitalService.getVitals(orgId, patientId, vitalType, pageable);
    }
}