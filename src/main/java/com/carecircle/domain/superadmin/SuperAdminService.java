package com.carecircle.domain.superadmin;

import com.carecircle.domain.organization.Organization;
import com.carecircle.domain.organization.OrganizationRepository;
import com.carecircle.domain.patient.PatientRepository;
import com.carecircle.domain.superadmin.dto.SuperAdminDto;
import com.carecircle.domain.user.User;
import com.carecircle.domain.user.UserRepository;
import com.carecircle.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository         userRepository;
    private final PatientRepository      patientRepository;

    // ── LIST ALL ORGS (paginated) ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<SuperAdminDto.OrgSummary> listAllOrgs(Pageable pageable) {
        return organizationRepository.findAll(pageable)
                .map(org -> {
                    int memberCount  = userRepository.findByOrganizationIdAndIsActiveTrue(org.getId()).size();
                    int patientCount = (int) patientRepository.countByOrganizationIdAndActiveTrue(org.getId());
                    return new SuperAdminDto.OrgSummary(
                            org.getId(),
                            org.getName(),
                            org.getPlanType(),
                            memberCount,
                            patientCount,
                            org.getCreatedAt()
                    );
                });
    }

    // ── GET ONE ORG IN DETAIL ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public SuperAdminDto.OrgDetail getOrgDetail(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

        List<SuperAdminDto.MemberSummary> members = userRepository
                .findByOrganizationIdAndIsActiveTrue(orgId)
                .stream()
                .map(u -> new SuperAdminDto.MemberSummary(
                        u.getId(),
                        u.getEmail(),
                        u.getFullName(),
                        u.getRole(),
                        u.isActive(),
                        u.getGoogleSubjectId() != null,
                        u.getCreatedAt()
                ))
                .collect(Collectors.toList());

        int patientCount = (int) patientRepository.countByOrganizationIdAndActiveTrue(orgId);

        return new SuperAdminDto.OrgDetail(
                org.getId(),
                org.getName(),
                org.getPlanType(),
                org.getCreatedAt(),
                members,
                patientCount,
                0   // totalDosesMarked — wire up with DoseEventRepository if needed
        );
    }

    // ── LIST ALL USERS ACROSS ALL ORGS (paginated) ───────────────────────────
    @Transactional(readOnly = true)
    public Page<SuperAdminDto.UserSummary> listAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(u -> new SuperAdminDto.UserSummary(
                        u.getId(),
                        u.getEmail(),
                        u.getFullName(),
                        u.getRole(),
                        u.getOrganization() != null ? u.getOrganization().getName() : null,
                        u.getOrganization() != null ? u.getOrganization().getId()   : null,
                        u.isActive(),
                        u.getGoogleSubjectId() != null,
                        u.getCreatedAt()
                ));
    }

    // ── DEACTIVATE AN ENTIRE ORG ──────────────────────────────────────────────
    // Nuclear option — use with care. Soft-deactivates the org record.
    // Does NOT cascade to patients/doses — data is preserved for audit.
    @Transactional
    public void deactivateOrg(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

        // Deactivate all members of this org
        List<User> members = userRepository.findByOrganizationIdAndIsActiveTrue(orgId);
        members.forEach(u -> u.setActive(false));
        userRepository.saveAll(members);

        log.warn("SUPER_ADMIN deactivated org: {} ({})", org.getName(), orgId);
    }

    // ── DEACTIVATE A SPECIFIC USER (any org) ──────────────────────────────────
    @Transactional
    public void deactivateUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getRole() == User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Cannot deactivate SUPER_ADMIN.");
        }

        user.setActive(false);
        userRepository.save(user);
        log.warn("SUPER_ADMIN deactivated user: {} ({})", user.getEmail(), userId);
    }
}