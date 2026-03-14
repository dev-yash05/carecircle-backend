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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Transactional
    public void refresh(String rawRefreshToken, HttpServletResponse response) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (!storedToken.isValid()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Refresh token expired or revoked. Please log in again.");
        }

        User user = storedToken.getUser();
        refreshTokenRepository.revokeAllByUserId(user.getId());

        // 🧠 SUPER_ADMIN has no org — pass null, JwtService handles it.
        UUID orgId = (user.getOrganization() != null)
                ? user.getOrganization().getId()
                : null;

        String newAccessToken  = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name(), orgId);
        String newRawRefresh   = jwtService.generateRefreshToken(user.getId());
        String newHash         = hashToken(newRawRefresh);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(newHash)
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshExpiryMs()))
                .isRevoked(false)
                .build());

        setAccessTokenCookie(response, newAccessToken);
        setRefreshTokenCookie(response, newRawRefresh);

        log.info("Token rotation complete — user: {}", user.getEmail());
    }

    @Transactional
    public void logout(String rawRefreshToken, HttpServletResponse response) {
        if (!rawRefreshToken.isBlank()) {
            String tokenHash = hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token ->
                    refreshTokenRepository.revokeAllByUserId(token.getUser().getId())
            );
        }
        clearCookie(response, "access_token",  "/");
        clearCookie(response, "refresh_token", "/api/v1/auth/refresh");
    }

    private void setAccessTokenCookie(HttpServletResponse response, String token) {
        Cookie c = new Cookie("access_token", token);
        c.setHttpOnly(true);
        c.setSecure(false);   // ← true in production
        c.setPath("/");
        c.setMaxAge((int) (jwtProperties.getExpiryMs() / 1000));
        response.addCookie(c);
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        Cookie c = new Cookie("refresh_token", token);
        c.setHttpOnly(true);
        c.setSecure(false);   // ← true in production
        c.setPath("/api/v1/auth/refresh");
        c.setMaxAge((int) (jwtProperties.getRefreshExpiryMs() / 1000));
        response.addCookie(c);
    }

    private void clearCookie(HttpServletResponse response, String name, String path) {
        Cookie c = new Cookie(name, "");
        c.setHttpOnly(true);
        c.setPath(path);
        c.setMaxAge(0);
        response.addCookie(c);
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