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
        // 🧠 Composite unique constraint: email must be unique per org,
        // but the same email CAN exist in different organizations.
        // This mirrors our schema: CONSTRAINT uq_user_email_per_org
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_email_per_org",
                columnNames = {"organization_id", "email"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true, exclude = "organization")
public class User extends BaseEntity {

    // 🧠 @ManyToOne with LAZY loading: Don't fetch the entire Organization
    // object every time you load a User. Only fetch it when you actually
    // call user.getOrganization(). This is a massive performance win.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Email
    @NotBlank
    @Column(nullable = false)
    private String email;

    @NotBlank
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    // 🧠 This is the Google "sub" claim — a permanent unique ID for the
    // Google account. Even if the user changes their Gmail address,
    // this ID stays the same. We use this to identify returning users.
    @Column(name = "google_subject_id", unique = true)
    private String googleSubjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role = Role.CAREGIVER;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public enum Role {
        ADMIN, CAREGIVER, VIEWER
    }
}