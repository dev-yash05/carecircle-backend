package com.carecircle.security;

import com.carecircle.config.JwtProperties;
import com.carecircle.domain.user.RefreshToken;
import com.carecircle.domain.user.RefreshTokenRepository;
import com.carecircle.domain.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

// =============================================================================
// 🧠 REFRESH TOKEN ROTATION — Why this pattern matters:
//
// Problem with long-lived access tokens:
//   If an access token is stolen (e.g. MITM, log leak), the attacker has
//   access for the entire 15-minute window. We can't revoke a stateless JWT.
//
// Solution — Refresh Token Rotation:
//   Access token: short-lived (15 min), stateless JWT
//   Refresh token: long-lived (7 days), stored as SHA-256 hash in DB
//
// On every refresh:
//   1. Validate the incoming refresh token (not revoked, not expired)
//   2. REVOKE it immediately (one-time use)
//   3. Issue a NEW refresh token + new access token
//
// Security guarantee:
//   If an attacker steals a refresh token and uses it first, the legitimate
//   user's next refresh will fail (token already revoked). This is a
//   detectable replay attack signal.
// =============================================================================

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    // -------------------------------------------------------------------------
    // CORE REFRESH LOGIC
    // -------------------------------------------------------------------------
    @Transactional
    public void refresh(String rawRefreshToken, HttpServletResponse response) {

        // 1. Hash the incoming cookie value — we store hashes, never raw tokens
        String tokenHash = hashToken(rawRefreshToken);

        // 2. Look up the token in the DB
        RefreshToken storedToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found in DB — possible replay attack or tampered cookie");
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
                });

        // 3. Validate: not revoked, not expired
        // 🧠 isValid() = !isRevoked && !isExpired() — defined on the entity
        if (!storedToken.isValid()) {
            log.warn("Refresh token invalid for user: {} — revoked={}, expired={}",
                    storedToken.getUser().getEmail(),
                    storedToken.isRevoked(),
                    storedToken.isExpired());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Refresh token has expired or been revoked. Please log in again.");
        }

        User user = storedToken.getUser();
        log.info("Refresh token rotation for user: {}", user.getEmail());

        // 4. REVOKE the current token — one-time use enforced
        // 🧠 We revoke ALL tokens for this user, not just this one.
        // If somehow two valid tokens exist (e.g. concurrent refresh race),
        // this cleans up all of them. The user gets exactly one active token.
        refreshTokenRepository.revokeAllByUserId(user.getId());

        // 5. Generate new token pair
        String newAccessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getOrganization().getId()
        );
        String newRawRefreshToken = jwtService.generateRefreshToken(user.getId());

        // 6. Store new refresh token hash in DB
        String newTokenHash = hashToken(newRawRefreshToken);
        RefreshToken newToken = RefreshToken.builder()
                .user(user)
                .tokenHash(newTokenHash)
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshExpiryMs()))
                .isRevoked(false)
                .build();
        refreshTokenRepository.save(newToken);

        // 7. Set new HttpOnly cookies — same pattern as OAuth2SuccessHandler
        setAccessTokenCookie(response, newAccessToken);
        setRefreshTokenCookie(response, newRawRefreshToken);

        log.info("Token rotation complete for user: {}", user.getEmail());
    }

    // -------------------------------------------------------------------------
    // LOGOUT — revoke all refresh tokens for the user
    // -------------------------------------------------------------------------
    @Transactional
    public void logout(String rawRefreshToken, HttpServletResponse response) {
        String tokenHash = hashToken(rawRefreshToken);

        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            refreshTokenRepository.revokeAllByUserId(token.getUser().getId());
            log.info("User logged out: {}", token.getUser().getEmail());
        });

        // Clear both cookies regardless — even if token not found
        clearCookie(response, "access_token", "/");
        clearCookie(response, "refresh_token", "/api/v1/auth/refresh");
    }

    // -------------------------------------------------------------------------
    // COOKIE HELPERS — mirrors OAuth2SuccessHandler exactly
    // -------------------------------------------------------------------------
    private void setAccessTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("access_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);        // Set true in production (HTTPS)
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtProperties.getExpiryMs() / 1000));
        response.addCookie(cookie);
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("refresh_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);        // Set true in production
        cookie.setPath("/api/v1/auth/refresh");   // Only sent to this endpoint
        cookie.setMaxAge((int) (jwtProperties.getRefreshExpiryMs() / 1000));
        response.addCookie(cookie);
    }

    private void clearCookie(HttpServletResponse response, String name, String path) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setPath(path);
        cookie.setMaxAge(0);            // MaxAge=0 tells the browser to delete it
        response.addCookie(cookie);
    }

    // -------------------------------------------------------------------------
    // SHA-256 HASH — same implementation as OAuth2SuccessHandler
    // -------------------------------------------------------------------------
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