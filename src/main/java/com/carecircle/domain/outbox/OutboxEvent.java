package com.carecircle.domain.outbox;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// 🧠 THE OUTBOX PATTERN ENTITY
//
// This is written in the SAME database transaction as your business logic.
// A background job (@Scheduled every 5s) reads PENDING rows and sends
// them to RabbitMQ. If RabbitMQ is down, rows stay PENDING and retry.
// This guarantees at-least-once delivery of every notification.

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // e.g. "DoseEvent" — which type of entity triggered this event
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    // The ID of the DoseEvent or VitalReading that triggered this
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    // e.g. "DOSE_TAKEN", "BP_ANOMALY_DETECTED"
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    // Full event data — the notification-module reads this to know
    // what message to send and to whom
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public enum OutboxStatus {
        PENDING, PROCESSED, FAILED
    }

    // Factory method — clean way to create an outbox event
    public static OutboxEvent of(String aggregateType, UUID aggregateId,
                                 String eventType, Map<String, Object> payload) {
        return OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .build();
    }
}