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
    // RECORD VITAL — with inline anomaly detection + outbox alert
    // -------------------------------------------------------------------------
    @Transactional
    public VitalDto.Response recordVital(UUID orgId, UUID patientId,
                                         VitalDto.CreateRequest request,
                                         User recordedBy) {

        Patient patient = patientRepository
                .findByIdAndOrganizationId(patientId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));

        VitalReading.VitalType vitalType;
        try {
            vitalType = VitalReading.VitalType.valueOf(request.getVitalType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid vital type: " + request.getVitalType() +
                    ". Valid types: BLOOD_PRESSURE, BLOOD_SUGAR, WEIGHT, SPO2, TEMPERATURE");
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

        // If anomalous — write OutboxEvent in SAME transaction (Outbox Pattern)
        // 🧠 Same guarantee as DOSE_TAKEN: if this transaction commits, the alert
        // is guaranteed to be delivered eventually via OutboxPublisher → RabbitMQ.
        if (anomaly.anomalous) {
            log.warn("ANOMALY DETECTED for patient: {} | type: {} | reason: {}",
                    patientId, vitalType, anomaly.reason);

            Map<String, Object> alertPayload = new HashMap<>(request.getReadingValue());
            alertPayload.put("vitalReadingId", saved.getId().toString());
            alertPayload.put("patientId", patientId.toString());
            alertPayload.put("patientName", patient.getFullName());
            alertPayload.put("vitalType", vitalType.name());
            alertPayload.put("anomalyReason", anomaly.reason);
            alertPayload.put("recordedBy", recordedBy.getFullName());
            alertPayload.put("measuredAt", saved.getMeasuredAt().toString());
            alertPayload.put("organizationId", orgId.toString());

            outboxEventRepository.save(OutboxEvent.of(
                    "VitalReading",
                    saved.getId(),
                    "BP_ANOMALY_DETECTED",
                    alertPayload
            ));

            log.info("BP_ANOMALY_DETECTED outbox event written for patient: {}", patientId);
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
    // ANOMALY DETECTION ENGINE
    // -------------------------------------------------------------------------
    private AnomalyResult detectAnomaly(VitalReading.VitalType type, Map<String, Object> value) {
        try {
            return switch (type) {
                case BLOOD_PRESSURE -> detectBpAnomaly(value);
                case BLOOD_SUGAR    -> detectBloodSugarAnomaly(value);
                case SPO2           -> detectSpo2Anomaly(value);
                case TEMPERATURE    -> detectTempAnomaly(value);
                case WEIGHT         -> AnomalyResult.normal();
            };
        } catch (Exception e) {
            log.warn("Could not evaluate anomaly for type: {} — {}", type, e.getMessage());
            return AnomalyResult.normal();
        }
    }

    private AnomalyResult detectBpAnomaly(Map<String, Object> value) {
        int systolic  = toInt(value.get("systolic"));
        int diastolic = toInt(value.get("diastolic"));
        if (systolic > 160) {
            return AnomalyResult.of("Systolic BP critically high: " + systolic + " mmHg (threshold: >160)");
        }
        if (diastolic > 100) {
            return AnomalyResult.of("Diastolic BP high: " + diastolic + " mmHg (threshold: >100)");
        }
        return AnomalyResult.normal();
    }

    private AnomalyResult detectBloodSugarAnomaly(Map<String, Object> value) {
        double glucose = toDouble(value.get("value"));
        if (glucose > 250) {
            return AnomalyResult.of("Blood sugar dangerously high: " + glucose + " mg/dL (threshold: >250)");
        }
        return AnomalyResult.normal();
    }

    private AnomalyResult detectSpo2Anomaly(Map<String, Object> value) {
        double spo2 = toDouble(value.get("value"));
        if (spo2 < 92) {
            return AnomalyResult.of("SpO2 critically low: " + spo2 + "% (threshold: <92%)");
        }
        return AnomalyResult.normal();
    }

    private AnomalyResult detectTempAnomaly(Map<String, Object> value) {
        double temp = toDouble(value.get("value"));
        if (temp > 38.5) {
            return AnomalyResult.of("Fever detected: " + temp + "°C (threshold: >38.5°C)");
        }
        return AnomalyResult.normal();
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------
    private int toInt(Object val) {
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }

    private double toDouble(Object val) {
        if (val instanceof Double d) return d;
        if (val instanceof Number n) return n.doubleValue();
        return Double.parseDouble(val.toString());
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

    // -------------------------------------------------------------------------
    // INNER CLASS: AnomalyResult
    // -------------------------------------------------------------------------
    // 🧠 FIX: The previous version used @lombok.Value on this inner class.
    // @lombok.Value generates a final class with all-args constructor + getters,
    // but it CONFLICTS with Spring's @org.springframework.beans.factory.annotation.Value
    // annotation at compile time — both are named @Value and the processor gets confused.
    // Solution: write the class manually. It's only 3 fields — no Lombok needed here.
    private static final class AnomalyResult {
        final boolean anomalous;
        final String reason;

        private AnomalyResult(boolean anomalous, String reason) {
            this.anomalous = anomalous;
            this.reason = reason;
        }

        static AnomalyResult normal() {
            return new AnomalyResult(false, null);
        }

        static AnomalyResult of(String reason) {
            return new AnomalyResult(true, reason);
        }
    }
}