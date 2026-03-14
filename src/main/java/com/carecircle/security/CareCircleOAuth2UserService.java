package com.carecircle.security;

import com.carecircle.config.AppProperties;
import com.carecircle.domain.organization.Organization;
import com.carecircle.domain.organization.OrganizationRepository;
import com.carecircle.domain.user.User;
import com.carecircle.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// =============================================================================
// 🧠 THE REVISED loadUser() — 4 ORDERED PATHS:
//
// Path 1 — SUPER_ADMIN:
//   email matches env var SUPER_ADMIN_EMAIL → role=SUPER_ADMIN, no org, no invite.
//   This check runs first, before any DB lookup beyond email.
//
// Path 2 — Returning user (normal daily flow):
//   findByGoogleSubjectId(sub) → found → update profile, return as-is.
//   This is the hot path — fires on 99% of logins after the first one.
//
// Path 3 — Pre-registered member (first login after Admin added them):
//   findByGoogleSubjectId → not found → findByEmail → found →
//   link googleSubjectId now, keep role + org that Admin assigned.
//   This is how CAREGIVER / VIEWER get into an org without self-registering.
//
// Path 4 — Brand new user (self-registration):
//   Neither sub nor email found → create new Organization + ADMIN user.
//   This is the original flow, unchanged — fires only for genuine new Admins.
// =============================================================================

@Slf4j
@Service
@RequiredArgsConstructor
public class CareCircleOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        // Let Spring fetch Google's user profile (calls Google's userinfo endpoint)
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // Extract the three fields we care about from Google's response
        String googleSubjectId = oAuth2User.getAttribute("sub");   // Permanent, never changes
        String email           = oAuth2User.getAttribute("email");
        String name            = oAuth2User.getAttribute("name");
        String avatarUrl       = oAuth2User.getAttribute("picture");

        log.info("OAuth2 login attempt — email: {}", email);

        // ── PATH 1: SUPER_ADMIN ──────────────────────────────────────────────
        // Checked before any org/role logic. Configured purely via env var.
        // The superAdminEmail check is case-insensitive for safety.
        if (!appProperties.getSuperAdminEmail().isBlank()
                && appProperties.getSuperAdminEmail().equalsIgnoreCase(email)) {

            log.info("SUPER_ADMIN login detected for: {}", email);

            User superAdmin = userRepository.findByEmail(email)
                    .orElseGet(() -> {
                        // First time this email logs in — create the SUPER_ADMIN user.
                        // No org — SUPER_ADMIN is above all organizations.
                        User u = new User();
                        u.setEmail(email);
                        u.setRole(User.Role.SUPER_ADMIN);
                        u.setActive(true);
                        return u;
                    });

            // Always keep profile fresh
            superAdmin.setGoogleSubjectId(googleSubjectId);
            superAdmin.setFullName(name);
            superAdmin.setAvatarUrl(avatarUrl);
            return new CareCircleOAuth2User(userRepository.save(superAdmin), oAuth2User.getAttributes());
        }

        // ── PATH 2: Returning user — found by Google sub ─────────────────────
        // Most common path after first login. The sub never changes even if
        // the user changes their Gmail address.
        var bySubject = userRepository.findByGoogleSubjectId(googleSubjectId);
        if (bySubject.isPresent()) {
            User user = bySubject.get();
            // Refresh display name + avatar in case they changed on Google
            user.setFullName(name);
            user.setAvatarUrl(avatarUrl);
            log.info("Returning user login — id: {}, role: {}", user.getId(), user.getRole());
            return new CareCircleOAuth2User(userRepository.save(user), oAuth2User.getAttributes());
        }

        // ── PATH 3: Pre-registered member — first login after Admin added them ─
        // Admin called POST /org/{id}/members with this email + CAREGIVER/VIEWER.
        // That created a User row with role+org already set, but googleSubjectId=NULL.
        // Now they log in for the first time — we link the Google identity.
        var byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User preRegistered = byEmail.get();

            if (!preRegistered.isActive()) {
                log.warn("Login attempt for deactivated user: {}", email);
                throw new OAuth2AuthenticationException("Account is deactivated. Contact your administrator.");
            }

            // Link their Google identity now — sub was null until this moment
            preRegistered.setGoogleSubjectId(googleSubjectId);
            preRegistered.setFullName(name);
            preRegistered.setAvatarUrl(avatarUrl);

            log.info("Pre-registered member first login — email: {}, role: {}, org: {}",
                    email, preRegistered.getRole(), preRegistered.getOrganization().getId());

            return new CareCircleOAuth2User(userRepository.save(preRegistered), oAuth2User.getAttributes());
        }

        // ── PATH 4: Brand new user — self-registration as ADMIN ──────────────
        // No existing record anywhere. Create a fresh org and make them its Admin.
        // This is the original flow, completely unchanged.
        log.info("New user self-registration — email: {}", email);
        User newUser = createNewAdminWithOrg(googleSubjectId, email, name, avatarUrl);
        return new CareCircleOAuth2User(newUser, oAuth2User.getAttributes());
    }

    // -------------------------------------------------------------------------
    // Extracted from the original createNewUser() — logic identical.
    // -------------------------------------------------------------------------
    private User createNewAdminWithOrg(String googleSubjectId, String email,
                                       String name, String avatarUrl) {
        Organization org = new Organization();
        org.setName(name + "'s Care Circle");
        org.setPlanType(Organization.PlanType.FREE);
        Organization savedOrg = organizationRepository.save(org);

        User user = new User();
        user.setGoogleSubjectId(googleSubjectId);
        user.setEmail(email);
        user.setFullName(name);
        user.setAvatarUrl(avatarUrl);
        user.setOrganization(savedOrg);
        user.setRole(User.Role.ADMIN);
        user.setActive(true);

        return userRepository.save(user);
    }
}