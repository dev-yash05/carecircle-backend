package com.carecircle.security;

import com.carecircle.domain.user.RefreshToken;
import com.carecircle.domain.user.User;
import com.carecircle.domain.user.UserRepository;
import com.carecircle.config.JwtProperties;
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
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final com.carecircle.domain.user.RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CareCircleOAuth2User oAuth2User = (CareCircleOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        // 1. Generate tokens
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(),
                user.getRole().name(), user.getOrganization().getId()
        );
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        // 2. Hash the refresh token before storing in DB
        // 🧠 We store a HASH, never the raw token.
        // If DB is compromised, attacker can't use the hashes.
        String tokenHash = hashToken(refreshToken);

        // 3. Revoke all existing refresh tokens for this user (security)
        refreshTokenRepository.revokeAllByUserId(user.getId());

        // 4. Save new refresh token hash in DB
        RefreshToken savedToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshExpiryMs()))
                .isRevoked(false)
                .build();
        refreshTokenRepository.save(savedToken);

        // 5. Set Access Token as HttpOnly cookie
        Cookie accessCookie = new Cookie("access_token", accessToken);
        accessCookie.setHttpOnly(true);     // JS cannot read this
        accessCookie.setSecure(false);      // Set true in production (HTTPS only)
        accessCookie.setPath("/");
        accessCookie.setMaxAge((int) (jwtProperties.getExpiryMs() / 1000));
        response.addCookie(accessCookie);

        // 6. Set Refresh Token as HttpOnly cookie
        Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false);     // Set true in production
        refreshCookie.setPath("/api/v1/auth/refresh");  // Only sent to refresh endpoint
        refreshCookie.setMaxAge((int) (jwtProperties.getRefreshExpiryMs() / 1000));
        response.addCookie(refreshCookie);

        log.info("JWT cookies set for user: {}", user.getEmail());

        // 7. Redirect to frontend dashboard
        getRedirectStrategy().sendRedirect(request, response, "http://localhost:3000/dashboard");
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