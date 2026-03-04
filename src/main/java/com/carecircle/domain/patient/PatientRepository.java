package com.carecircle.domain.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

// 🧠 SENIOR SIGNAL: JpaRepository gives you 20+ methods for free:
// save(), findById(), findAll(), delete(), count(), existsById()...
// You NEVER write SQL for basic CRUD. Only write custom queries
// when Spring Data can't derive them from the method name.

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    // 🧠 METHOD NAME QUERY: Spring Data reads the method name and
    // auto-generates the SQL. No @Query needed.
    // Generates: SELECT * FROM patients WHERE organization_id = ? AND is_active = ?
    Page<Patient> findByOrganizationIdAndActive(
            UUID organizationId,
            boolean active,
            Pageable pageable
    );

    long countByOrganizationIdAndActive(UUID organizationId, boolean active);

    // 🧠 WHY this custom @Query?
    // We need to check if a patient belongs to a specific org BEFORE
    // returning it. Without this, a caregiver from Org A could fetch
    // a patient from Org B just by guessing the UUID.
    // This is the foundation of multi-tenant data isolation.
    @Query("SELECT p FROM Patient p WHERE p.id = :id AND p.organization.id = :orgId")
    Optional<Patient> findByIdAndOrganizationId(
            @Param("id") UUID id,
            @Param("orgId") UUID orgId
    );

}