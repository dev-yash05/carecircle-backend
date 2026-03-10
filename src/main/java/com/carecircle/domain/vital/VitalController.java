package com.carecircle.domain.vital;

import com.carecircle.domain.user.User;
import com.carecircle.domain.vital.dto.VitalDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Vitals", description = "Record and retrieve patient vital readings")
@RestController
@RequestMapping("/api/v1/organizations/{orgId}/patients/{patientId}/vitals")
@RequiredArgsConstructor
public class VitalController {

    private final VitalService vitalService;

    @Operation(summary = "Record a vital reading",
            description = "Records a vital sign. If BLOOD_PRESSURE systolic > 160, marks as anomalous and triggers BP_ANOMALY_DETECTED outbox event.")
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

    @Operation(summary = "Get vital readings (time-series)",
            description = "Returns paginated vital readings sorted newest-first. Use vitalType param to filter.")
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