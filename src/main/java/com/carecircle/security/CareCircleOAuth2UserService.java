package com.carecircle.security;

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
// 🧠 HOW OAUTH2 WORKS (the part Spring hides from you):
//
// 1. User clicks "Login with Google"
// 2. Spring redirects to Google's auth URL with your client_id
// 3. User approves → Google redirects back with a one-time "code"
// 4. Spring exchanges the code for an access token (server-to-server)
// 5. Spring uses the access token to call Google's userinfo endpoint
// 6. Google returns: { sub, email, name, picture }
// 7. THIS METHOD is called with that user data
//
// Our job here: take Google's user data and find/create OUR user in OUR DB.
// We then return a principal that Spring Security uses for the session.
// =============================================================================

@Slf4j
@Service
@RequiredArgsConstructor
public class CareCircleOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. Let Spring fetch Google's user profile (calls Google's userinfo API)
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 2. Extract attributes from Google's response
        String googleSubjectId = oAuth2User.getAttribute("sub");    // Permanent Google ID
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String avatarUrl = oAuth2User.getAttribute("picture");

        log.info("OAuth2 login attempt for email: {}", email);

        // 3. Find existing user by Google subject ID (most reliable identifier)
        //    OR create a new user if first login
        User user = userRepository.findByGoogleSubjectId(googleSubjectId)
                .orElseGet(() -> createNewUser(googleSubjectId, email, name, avatarUrl));

        // 4. Update profile info in case it changed on Google side
        user.setFullName(name);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        log.info("OAuth2 login successful for user: {}", user.getId());

        // 5. Return our custom principal that wraps both our User and Google's attributes
        return new CareCircleOAuth2User(user, oAuth2User.getAttributes());
    }

    private User createNewUser(String googleSubjectId, String email,
                               String name, String avatarUrl) {
        log.info("Creating new user for email: {}", email);

        // 🧠 AUTO-CREATE ORGANIZATION for new users:
        // When someone signs up for the first time, we automatically
        // create a personal organization for them. They become the ADMIN.
        // Later they can invite caregivers to their organization.
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
        user.setRole(User.Role.ADMIN);   // First user = Admin of their circle
        user.setActive(true);

        return userRepository.save(user);
    }
}