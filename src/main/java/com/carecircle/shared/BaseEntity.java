package com.carecircle.shared;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

// 🧠 SENIOR SIGNAL: Instead of copy-pasting id/createdAt/updatedAt
// into every entity, we extract them into a base class.
// All entities extend this. DRY principle (Don't Repeat Yourself).

@Getter
@Setter
@MappedSuperclass  // Not a table itself — JPA merges these fields into child tables
@EntityListeners(AuditingEntityListener.class)  // Auto-fills createdAt/updatedAt
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // 🧠 @CreatedDate: Spring Data auto-sets this when entity is first saved.
    // updatable = false means Hibernate will NEVER update this column after insert.
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    // 🧠 @LastModifiedDate: Spring Data auto-updates this on every save().
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}