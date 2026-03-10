package com.carecircle.domain.audit;

import com.carecircle.domain.audit.dto.AuditLogDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Audit", description = "Append-only audit trail for patient records")
@RestController
@RequestMapping("/api/v1/organizations/{orgId}/patients/{patientId}/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    // GET /api/v1/organizations/{orgId}/patients/{patientId}/audit
    // Returns paginated audit trail sorted created_at DESC (newest first)
    @Operation(summary = "Get patient audit trail",
            description = "Returns immutable append-only history of all actions on this patient. Sorted newest first.")
    @GetMapping
    public Page<AuditLogDto.Response> getAuditLog(
            @PathVariable UUID orgId,
            @PathVariable UUID patientId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return auditService.getPatientAuditLog(orgId, patientId, pageable);
    }
}