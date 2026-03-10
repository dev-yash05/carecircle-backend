package com.carecircle.domain.report;

import com.carecircle.domain.medication.DoseEvent;
import com.carecircle.domain.medication.DoseEventRepository;
import com.carecircle.domain.patient.Patient;
import com.carecircle.domain.patient.PatientRepository;
import com.carecircle.domain.vital.VitalReading;
import com.carecircle.domain.vital.VitalReadingRepository;
import com.carecircle.shared.exception.ResourceNotFoundException;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

// =============================================================================
// 🧠 PDF REPORT ARCHITECTURE
//
// Library choice: OpenPDF (com.github.librepdf:openpdf)
//   - MIT/LGPL licensed (iText was re-licensed to AGPL in v5)
//   - Drop-in replacement for iText 2.x API
//   - Actively maintained fork
//
// Response pattern: returns byte[] → controller streams it via HttpServletResponse
// with Content-Type: application/pdf + Content-Disposition: attachment.
//
// We don't use Spring's ResponseEntity<byte[]> for PDF because we need to
// set headers AND stream the body. HttpServletResponse gives us both.
//
// Thread safety: Each report request creates its own Document + ByteArrayOutputStream.
// No shared state — safe for concurrent requests.
// =============================================================================

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientReportService {

    private final PatientRepository patientRepository;
    private final DoseEventRepository doseEventRepository;
    private final VitalReadingRepository vitalReadingRepository;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // -------------------------------------------------------------------------
    // GENERATE MONTHLY REPORT
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public byte[] generateMonthlyReport(UUID orgId, UUID patientId, String month) {
        // Validate month format: "2026-03"
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid month format. Use YYYY-MM (e.g. 2026-03)");
        }

        Patient patient = patientRepository
                .findByIdAndOrganizationId(patientId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        // Month window in UTC (DB stores UTC)
        Instant from = yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = yearMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC);

        // Fetch data
        List<DoseEvent> doses = doseEventRepository
                .findByPatientIdOrderByScheduledAtDesc(patientId, Pageable.unpaged())
                .stream()
                .filter(d -> !d.getScheduledAt().isBefore(from) && !d.getScheduledAt().isAfter(to))
                .toList();

        List<VitalReading> vitals = vitalReadingRepository.findByPatientIdInWindow(patientId, from, to);

        log.info("Generating report: patient={} month={} doses={} vitals={}",
                patientId, month, doses.size(), vitals.size());

        return buildPdf(patient, yearMonth, doses, vitals);
    }

    // -------------------------------------------------------------------------
    // PDF BUILDER
    // -------------------------------------------------------------------------
    private byte[] buildPdf(Patient patient, YearMonth yearMonth,
                            List<DoseEvent> doses, List<VitalReading> vitals) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document doc = new Document(PageSize.A4, 36, 36, 54, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();

        // ── Fonts ──────────────────────────────────────────────────────────
        Font titleFont   = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(30, 30, 80));
        Font headerFont  = new Font(Font.HELVETICA, 13, Font.BOLD, new Color(30, 30, 80));
        Font normalFont  = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);
        Font boldFont    = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
        Font smallFont   = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
        Font redFont     = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(180, 0, 0));

        // ── Header block ───────────────────────────────────────────────────
        Paragraph title = new Paragraph("CareCircle — Monthly Health Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);
        doc.add(new Paragraph(" "));

        Paragraph meta = new Paragraph(
                "Patient: " + patient.getFullName() + "\n" +
                        "DOB: " + patient.getDateOfBirth().format(DATE_FMT) + "\n" +
                        "Report period: " + yearMonth.getMonth().getDisplayName(
                        java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                        + " " + yearMonth.getYear() + "\n" +
                        "Generated: " + LocalDateTime.now(IST).format(DATETIME_FMT) + " IST",
                normalFont
        );
        doc.add(meta);
        addDivider(doc);

        // ── Dose Events Summary ────────────────────────────────────────────
        doc.add(new Paragraph("Medication Adherence", headerFont));
        doc.add(new Paragraph(" "));

        long taken   = doses.stream().filter(d -> d.getStatus() == DoseEvent.DoseStatus.TAKEN).count();
        long skipped = doses.stream().filter(d -> d.getStatus() == DoseEvent.DoseStatus.SKIPPED).count();
        long missed  = doses.stream().filter(d -> d.getStatus() == DoseEvent.DoseStatus.MISSED).count();
        long pending = doses.stream().filter(d -> d.getStatus() == DoseEvent.DoseStatus.PENDING).count();
        int total    = doses.size();

        Paragraph summary = new Paragraph(
                "Total doses scheduled: " + total + "\n" +
                        "  Taken:   " + taken + (total > 0 ? " (" + pct(taken, total) + "%)" : "") + "\n" +
                        "  Skipped: " + skipped + "\n" +
                        "  Missed:  " + missed + "\n" +
                        "  Pending: " + pending,
                normalFont
        );
        doc.add(summary);
        doc.add(new Paragraph(" "));

        if (!doses.isEmpty()) {
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3f, 2f, 1.5f, 2f});
            addTableHeader(table, boldFont, "Medication", "Scheduled At", "Status", "Actioned By");

            for (DoseEvent d : doses) {
                Font statusFont = d.getStatus() == DoseEvent.DoseStatus.TAKEN ? normalFont :
                        d.getStatus() == DoseEvent.DoseStatus.MISSED ? redFont : smallFont;

                addCell(table, d.getSchedule().getMedicationName() + " " + d.getSchedule().getDosage(), normalFont);
                addCell(table, formatInstant(d.getScheduledAt()), normalFont);
                addCell(table, d.getStatus().name(), statusFont);
                addCell(table, d.getActionedBy() != null ? d.getActionedBy().getFullName() : "—", normalFont);
            }
            doc.add(table);
        } else {
            doc.add(new Paragraph("No dose events recorded this month.", smallFont));
        }

        addDivider(doc);

        // ── Vital Readings ────────────────────────────────────────────────
        doc.add(new Paragraph("Vital Signs", headerFont));
        doc.add(new Paragraph(" "));

        if (!vitals.isEmpty()) {
            PdfPTable vTable = new PdfPTable(4);
            vTable.setWidthPercentage(100);
            vTable.setWidths(new float[]{2f, 2.5f, 3f, 1.5f});
            addTableHeader(vTable, boldFont, "Type", "Measured At", "Reading", "Anomalous");

            for (VitalReading v : vitals) {
                Font rowFont = v.isAnomalous() ? redFont : normalFont;
                addCell(vTable, v.getVitalType().name(), rowFont);
                addCell(vTable, formatInstant(v.getMeasuredAt()), normalFont);
                addCell(vTable, v.getReadingValue().toString(), rowFont);
                addCell(vTable, v.isAnomalous() ? "⚠ YES" : "No", rowFont);
            }
            doc.add(vTable);
        } else {
            doc.add(new Paragraph("No vital readings recorded this month.", smallFont));
        }

        // ── Footer ────────────────────────────────────────────────────────
        addDivider(doc);
        Paragraph footer = new Paragraph(
                "This report was generated automatically by CareCircle. " +
                        "For clinical decisions, consult a qualified healthcare professional.",
                smallFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);

        doc.close();
        return out.toByteArray();
    }

    // ── PDF Helpers ───────────────────────────────────────────────────────────

    private void addTableHeader(PdfPTable table, Font font, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font));
            cell.setBackgroundColor(new Color(230, 235, 245));
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "—", font));
        cell.setPadding(4);
        cell.setBorderColor(new Color(200, 200, 200));
        table.addCell(cell);
    }

    private void addDivider(Document doc) throws DocumentException {
        Paragraph divider = new Paragraph(" ");
        divider.setSpacingBefore(4);
        divider.setSpacingAfter(4);
        doc.add(divider);
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "—";
        return LocalDateTime.ofInstant(instant, IST).format(DATETIME_FMT);
    }

    private long pct(long part, int total) {
        return total == 0 ? 0 : Math.round(100.0 * part / total);
    }
}