package com.carecircle.domain.organization;

import com.carecircle.shared.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Organization extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    // 🧠 @Enumerated(STRING): Store "PREMIUM" in DB, not "1".
    // If you use ORDINAL (the default), adding a new enum value in the
    // middle breaks all existing data. STRING is always safer.
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 50)
    private PlanType planType = PlanType.FREE;

    public enum PlanType {
        FREE, PREMIUM, ENTERPRISE
    }
}