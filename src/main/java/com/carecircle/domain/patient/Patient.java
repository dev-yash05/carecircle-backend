package com.carecircle.domain.patient;

import com.carecircle.domain.organization.Organization;
import com.carecircle.shared.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, exclude = "organization")
public class Patient extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @NotBlank
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @NotNull
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    @Column(name = "blood_type", length = 5)
    private String bloodType;

    // 🧠 JSONB in PostgreSQL + Hibernate:
    // We use the 'hypersistence-utils' library to map a Java Map
    // to a PostgreSQL JSONB column. This lets you store flexible
    // data like allergies, emergency contacts, doctor info without
    // creating 10 nullable columns.
    //
    // Query example in JPQL:
    // SELECT p FROM Patient p WHERE p.metadata -> 'allergies' IS NOT NULL
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}