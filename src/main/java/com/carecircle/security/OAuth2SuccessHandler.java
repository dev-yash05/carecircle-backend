package com.carecircle.security;

import com.carecircle.domain.user.RefreshToken;
import com.carecircle.domain.user.User;
import com.carecircle.config.JwtProperties;
import com.carecircle.domain.user.RefreshTokenRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        CareCircleOAuth2User oAuth2User = (CareCircleOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        // 🧠 SUPER_ADMIN has no organization — pass null orgId.
        // JwtService.generateAccessToken() accepts nullable orgId.
        UUID orgId = (user.getOrganization() != null)
                ? user.getOrganization().getId()
                : null;

        // 1. Generate tokens
        String accessToken  = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name(), orgId);
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        // 2. Hash refresh token before storing (never store raw token)
        String tokenHash = hashToken(refreshToken);

        // 3. Revoke all existing refresh tokens for this user (one session at a time)
        refreshTokenRepository.revokeAllByUserId(user.getId());

        // 4. Persist new refresh token hash
        RefreshToken savedToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshExpiryMs()))
                .isRevoked(false)
                .build();
        refreshTokenRepository.save(savedToken);

        // 5. Set access token as HttpOnly cookie
        Cookie accessCookie = new Cookie("access_token", accessToken);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(false);   // ← Set true in production (HTTPS)
        accessCookie.setPath("/");
        accessCookie.setMaxAge((int) (jwtProperties.getExpiryMs() / 1000));
        response.addCookie(accessCookie);

        // 6. Set refresh token as HttpOnly cookie (scoped to refresh endpoint only)
        Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);  // ← Set true in production
        refreshCookie.setPath("/api/v1/auth/refresh");
        refreshCookie.setMaxAge((int) (jwtProperties.getRefreshExpiryMs() / 1000));
        response.addCookie(refreshCookie);

        log.info("Login success — user: {}, role: {}", user.getEmail(), user.getRole());

        // 7. Redirect to the correct frontend page based on role
        String redirectUrl = switch (user.getRole()) {
            case SUPER_ADMIN -> "http://localhost:3000/superadmin";
            default          -> "http://localhost:3000/dashboard";
        };

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}