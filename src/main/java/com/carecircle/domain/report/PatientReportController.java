package com.carecircle.domain.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@Tag(name = "Reports", description = "Generate PDF monthly health reports")
@RestController
@RequestMapping("/api/v1/organizations/{orgId}/patients/{patientId}/report")
@RequiredArgsConstructor
public class PatientReportController {

    private final PatientReportService reportService;

    // GET /api/v1/organizations/{orgId}/patients/{patientId}/report?month=2026-03
    @Operation(
            summary = "Generate monthly health report (PDF)",
            description = "Generates a PDF report aggregating dose adherence and vital signs for the given month. " +
                    "Streams the file directly — browser will prompt download."
    )
    @GetMapping
    public void generateReport(
            @PathVariable UUID orgId,
            @PathVariable UUID patientId,
            @Parameter(description = "Month in YYYY-MM format", example = "2026-03")
            @RequestParam String month,
            HttpServletResponse response
    ) throws IOException {

        byte[] pdf = reportService.generateMonthlyReport(orgId, patientId, month);

        // 🧠 Why HttpServletResponse instead of ResponseEntity<byte[]>?
        // We need to set Content-Disposition AND stream bytes efficiently.
        // HttpServletResponse.getOutputStream() streams without buffering the full
        // response in memory — better for large PDFs.
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"carecircle-report-" + month + ".pdf\"");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }
}