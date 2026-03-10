package com.carecircle.domain.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// =============================================================================
// 🧠 AUDIT LOG — Senior Design Decisions
//
// 1. APPEND-ONLY. No @Version, no updatedAt. Each mutation = new row.
//    History is immutable. Never UPDATE or DELETE this table.
//
// 2. NO @ManyToOne / NO FOREIGN KEYS.
//    Audit logs must outlive the entities they describe.
//    If patient is deleted, audit of their care stays. FK would break this.
//
// 3. DENORMALIZED actor_email. Captured at write time.
//    If the actor's account is deleted, the log still shows who acted.
//
// 4. Does NOT extend BaseEntity (which adds updatedAt — wrong for audit).
//
// 5. ip_address uses PostgreSQL native `inet` type.
//    🧠 WHY? PostgreSQL's inet type validates IP addresses at the DB level,
//    stores IPv4 in 7 bytes and IPv6 in 19 bytes (vs 45 bytes for varchar),
//    and enables subnet queries like WHERE ip_address << '192.168.0.0/16'.
//    We use @JdbcTypeCode(SqlTypes.OTHER) to tell Hibernate to pass the
//    value through as-is without type conversion. Hibernate's validator
//    sees columnDefinition="inet" and stops expecting varchar.
// =============================================================================

@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(nullable = false, length = 100)
    private String action;        // "DOSE_MARKED_TAKEN" | "VITAL_RECORDED" | "PATIENT_UPDATED"

    @Column(name = "entity_type", length = 100)
    private String entityType;   // "DoseEvent" | "VitalReading" | "Patient"

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private Map<String, Object> oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private Map<String, Object> newValue;

    // 🧠 PostgreSQL inet type — NOT varchar.
    // @JdbcTypeCode(SqlTypes.OTHER) tells Hibernate to pass the string value
    // through to the JDBC driver as-is. The PG JDBC driver accepts a plain
    // String for inet columns (e.g. "192.168.1.1" or "::1").
    // columnDefinition = "inet" satisfies Hibernate's ddl-auto: validate check.
    @JdbcTypeCode(SqlTypes.OTHER)
    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}