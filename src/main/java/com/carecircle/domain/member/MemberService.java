package com.carecircle.domain.member;

import com.carecircle.domain.member.dto.MemberDto;
import com.carecircle.domain.organization.Organization;
import com.carecircle.domain.organization.OrganizationRepository;
import com.carecircle.domain.user.User;
import com.carecircle.domain.user.UserRepository;
import com.carecircle.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// =============================================================================
// 🧠 HOW MEMBER PRE-REGISTRATION WORKS:
//
// 1. Admin calls POST /organizations/{orgId}/members
//    Body: { "email": "ramesh@gmail.com", "role": "CAREGIVER" }
//
// 2. This service creates a User row with:
//    - email = "ramesh@gmail.com"
//    - role  = CAREGIVER
//    - organizationId = anjali's org
//    - googleSubjectId = NULL   ← the key: they haven't logged in yet
//
// 3. Admin tells Ramesh: "Go to the app and sign in with Google"
//
// 4. Ramesh signs in with ramesh@gmail.com via Google.
//    CareCircleOAuth2UserService.loadUser() runs:
//      a. Not SUPER_ADMIN email? → skip
//      b. findByGoogleSubjectId(sub)? → null, not found → skip
//      c. findByEmail("ramesh@gmail.com")? → FOUND → links his sub, done ✓
//
// 5. Ramesh lands on the dashboard as CAREGIVER in Anjali's org.
//    No invite link, no email service needed. Works today.
// =============================================================================

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final UserRepository         userRepository;
    private final OrganizationRepository organizationRepository;

    // ── ADD MEMBER ────────────────────────────────────────────────────────────
    @Transactional
    public MemberDto.Response addMember(UUID orgId, MemberDto.CreateRequest request,
                                        User actingAdmin) {

        // 1. Verify the org exists
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));

        // 2. SUPER_ADMIN can add to any org.
        //    ADMIN can only add to their own org — enforce that here.
        if (actingAdmin.getRole() == User.Role.ADMIN) {
            if (!actingAdmin.getOrganization().getId().equals(orgId)) {
                throw new IllegalArgumentException(
                        "You can only add members to your own organization.");
            }
        }

        // 3. Admins cannot assign ADMIN or SUPER_ADMIN roles via this endpoint.
        //    That would be a privilege escalation.
        if (request.role() == User.Role.ADMIN || request.role() == User.Role.SUPER_ADMIN) {
            throw new IllegalArgumentException(
                    "You can only assign CAREGIVER or VIEWER roles to new members.");
        }

        // 4. Check if this email already exists anywhere in the system
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new IllegalStateException(
                    "A user with email '" + request.email() + "' already exists. " +
                            "If they need to be re-added to this org, contact support.");
        }

        // 5. Pre-register the user — googleSubjectId is NULL intentionally.
        //    It gets filled in on their first Google login (Path 3 in loadUser()).
        User newMember = new User();
        newMember.setEmail(request.email());
        newMember.setRole(request.role());
        newMember.setOrganization(org);
        // fullName and avatarUrl are null until they log in — that's fine,
        // we don't know their real name until Google tells us.
        newMember.setFullName(request.email()); // temporary placeholder
        newMember.setActive(true);
        // googleSubjectId intentionally left null

        User saved = userRepository.save(newMember);
        log.info("Pre-registered member — email: {}, role: {}, org: {}",
                request.email(), request.role(), orgId);

        return toResponse(saved);
    }

    // ── LIST MEMBERS ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<MemberDto.Response> listMembers(UUID orgId, User actingUser) {

        // ADMIN can only see their own org
        if (actingUser.getRole() == User.Role.ADMIN) {
            if (!actingUser.getOrganization().getId().equals(orgId)) {
                throw new IllegalArgumentException(
                        "You can only view members of your own organization.");
            }
        }

        return userRepository.findByOrganizationIdAndIsActiveTrue(orgId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── REMOVE MEMBER ─────────────────────────────────────────────────────────
    @Transactional
    public void removeMember(UUID orgId, UUID userId, User actingAdmin) {

        // ADMIN can only remove from their own org
        if (actingAdmin.getRole() == User.Role.ADMIN) {
            if (!actingAdmin.getOrganization().getId().equals(orgId)) {
                throw new IllegalArgumentException(
                        "You can only remove members from your own organization.");
            }
        }

        // Guard: don't let anyone deactivate themselves
        if (actingAdmin.getId().equals(userId)) {
            throw new IllegalArgumentException(
                    "You cannot remove yourself from the organization.");
        }

        int updated = userRepository.deactivateMember(userId, orgId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Member", userId);
        }

        log.info("Member deactivated — userId: {}, org: {}, by: {}",
                userId, orgId, actingAdmin.getEmail());
    }

    // ── MAPPER ────────────────────────────────────────────────────────────────
    private MemberDto.Response toResponse(User user) {
        return new MemberDto.Response(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.isActive(),
                user.getGoogleSubjectId() != null, // hasLoggedIn
                user.getCreatedAt()
        );
    }
}