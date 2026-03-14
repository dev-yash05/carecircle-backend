package com.carecircle.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Used on every returning user's request — checked first in loadUser()
    Optional<User> findByGoogleSubjectId(String googleSubjectId);

    // Used for pre-registered members (CAREGIVER/VIEWER added by Admin before
    // they have ever logged in — their googleSubjectId is still NULL at this point)
    Optional<User> findByEmail(String email);

    // Admin views their org's member list
    List<User> findByOrganizationIdAndIsActiveTrue(UUID organizationId);

    // SUPER_ADMIN: paginated list of ALL users across all orgs
    Page<User> findAll(Pageable pageable);

    // Used when Admin removes a member
    @Modifying
    @Query("UPDATE User u SET u.isActive = false WHERE u.id = :userId AND u.organization.id = :orgId")
    int deactivateMember(@Param("userId") UUID userId, @Param("orgId") UUID orgId);
}