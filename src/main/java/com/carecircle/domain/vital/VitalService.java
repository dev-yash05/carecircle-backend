package com.carecircle.domain.vital;

import com.carecircle.domain.outbox.OutboxEvent;
import com.carecircle.domain.outbox.OutboxEventRepository;
import com.carecircle.domain.patient.Patient;
import com.carecircle.domain.patient.PatientRepository;
import com.carecircle.domain.user.User;
import com.carecircle.domain.vital.dto.VitalDto;
import com.carecircle.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VitalService {

    private final VitalReadingRepository vitalReadingRepository;
    private final PatientRepository patientRepository;
    private final OutboxEventRepository outboxEventRepository;

    // -------------------------------------------------------------------------
    // RECORD VITAL — with anomaly detection + outbox alert
    // -------------------------------------------------------------------------
    @Transactional
    public VitalDto.Response recordVital(UUID orgId, UUID patientId,
                                         VitalDto.CreateRequest request, User recordedBy) {
        Patient patient = patientRepository
                .findByIdAndOrganizationId(patientId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        VitalReading.VitalType vitalType;
        try {
            vitalType = VitalReading.VitalType.valueOf(request.getVitalType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid vital type: " + request.getVitalType());
        }

        AnomalyResult anomaly = detectAnomaly(vitalType, request.getReadingValue());

        VitalReading reading = VitalReading.builder()
                .patient(patient)
                .recordedBy(recordedBy)
                .vitalType(vitalType)
                .readingValue(request.getReadingValue())
                .measuredAt(request.getMeasuredAt() != null ? request.getMeasuredAt() : Instant.now())
                .anomalous(anomaly.anomalous)
                .alertTriggered(anomaly.anomalous)
                .build();

        VitalReading saved = vitalReadingRepository.save(reading);

        // Write OutboxEvent in SAME @Transactional — Transactional Outbox Pattern
        if (anomaly.anomalous) {
            log.warn("ANOMALY: patient={} type={} reason={}", patientId, vitalType, anomaly.reason);

            Map<String, Object> payload = new HashMap<>(request.getReadingValue());
            payload.put("vitalReadingId", saved.getId().toString());
            payload.put("patientId", patientId.toString());
            payload.put("patientName", patient.getFullName());
            payload.put("vitalType", vitalType.name());
            payload.put("anomalyReason", anomaly.reason);
            payload.put("recordedBy", recordedBy.getFullName());
            payload.put("measuredAt", saved.getMeasuredAt().toString());
            payload.put("organizationId", orgId.toString());

            outboxEventRepository.save(
                    OutboxEvent.of("VitalReading", saved.getId(), "BP_ANOMALY_DETECTED", payload)
            );
        }

        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // GET VITALS — paginated time-series
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public Page<VitalDto.Response> getVitals(UUID orgId, UUID patientId,
                                             String vitalTypeStr, Pageable pageable) {
        patientRepository.findByIdAndOrganizationId(patientId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        VitalReading.VitalType vitalType;
        try {
            vitalType = VitalReading.VitalType.valueOf(vitalTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid vital type: " + vitalTypeStr);
        }

        return vitalReadingRepository
                .findByPatientIdAndVitalTypeOrderByMeasuredAtDesc(patientId, vitalType, pageable)
                .map(this::toResponse);
    }

    // -------------------------------------------------------------------------
    // ANOMALY DETECTION
    // -------------------------------------------------------------------------
    private AnomalyResult detectAnomaly(VitalReading.VitalType type, Map<String, Object> value) {
        try {
            return switch (type) {
                case BLOOD_PRESSURE -> {
                    int sys = toInt(value.get("systolic"));
                    int dia = toInt(value.get("diastolic"));
                    if (sys > 160) yield AnomalyResult.of("Systolic BP critically high: " + sys + " mmHg (>160)");
                    if (dia > 100) yield AnomalyResult.of("Diastolic BP high: " + dia + " mmHg (>100)");
                    yield AnomalyResult.normal();
                }
                case BLOOD_SUGAR -> {
                    double g = toDouble(value.get("value"));
                    yield g > 250 ? AnomalyResult.of("Blood sugar high: " + g + " mg/dL (>250)") : AnomalyResult.normal();
                }
                case SPO2 -> {
                    double s = toDouble(value.get("value"));
                    yield s < 92 ? AnomalyResult.of("SpO2 critically low: " + s + "% (<92)") : AnomalyResult.normal();
                }
                case TEMPERATURE -> {
                    double t = toDouble(value.get("value"));
                    yield t > 38.5 ? AnomalyResult.of("Fever: " + t + "°C (>38.5)") : AnomalyResult.normal();
                }
                case WEIGHT -> AnomalyResult.normal();
            };
        } catch (Exception e) {
            log.warn("Could not evaluate anomaly for {} — {}", type, e.getMessage());
            return AnomalyResult.normal();
        }
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }

    private VitalDto.Response toResponse(VitalReading v) {
        return VitalDto.Response.builder()
                .id(v.getId())
                .patientId(v.getPatient().getId())
                .patientName(v.getPatient().getFullName())
                .vitalType(v.getVitalType().name())
                .readingValue(v.getReadingValue())
                .measuredAt(v.getMeasuredAt())
                .anomalous(v.isAnomalous())
                .alertTriggered(v.isAlertTriggered())
                .recordedByName(v.getRecordedBy().getFullName())
                .createdAt(v.getCreatedAt())
                .build();
    }

    // Plain inner class — avoids @lombok.Value vs Spring @Value conflict
    private static final class AnomalyResult {
        final boolean anomalous;
        final String reason;
        private AnomalyResult(boolean a, String r) { this.anomalous = a; this.reason = r; }
        static AnomalyResult normal() { return new AnomalyResult(false, null); }
        static AnomalyResult of(String r) { return new AnomalyResult(true, r); }
    }
}