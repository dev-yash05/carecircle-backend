package com.carecircle.domain.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    // JpaRepository gives us findById(), save(), delete() for free
    // No custom queries needed here yet
}