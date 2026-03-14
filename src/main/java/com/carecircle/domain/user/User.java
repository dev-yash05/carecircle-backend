package com.carecircle.domain.user;

import com.carecircle.domain.organization.Organization;
import com.carecircle.shared.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(
        name = "users",
        // 🧠 Global email uniqueness (changed from per-org in V1).
        // One real-world person = one account in the system.
        // Multi-org membership will be handled via a junction table in Sprint 7.
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_email_global",
                columnNames = {"email"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, exclude = "organization")
public class User extends BaseEntity {

    // 🧠 SUPER_ADMIN has no organization — they are above all orgs.
    // optional = true + nullable = true lets Hibernate insert a NULL FK.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "organization_id", nullable = true)
    private Organization organization;

    @Email
    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    // 🧠 NULLABLE: Pre-registered users (CAREGIVER / VIEWER added by an Admin
    // before they have ever signed in) have no Google sub yet. It gets filled
    // in on their very first Google login inside CareCircleOAuth2UserService.
    @Column(name = "google_subject_id", unique = true)
    private String googleSubjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role = Role.CAREGIVER;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public enum Role {
        // 🧠 Declaration order matters for documentation clarity:
        // Highest privilege → lowest privilege
        SUPER_ADMIN,  // You — sees ALL orgs, ALL data. Set via env var, not UI.
        ADMIN,        // Org owner — creates org, adds members, manages patients/meds.
        CAREGIVER,    // On-ground — marks doses, records vitals. Cannot edit patients.
        VIEWER        // Family/remote — read-only. Gets real-time alerts. Zero write access.
    }
}