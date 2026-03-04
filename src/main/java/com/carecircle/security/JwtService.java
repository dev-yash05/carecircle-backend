package com.carecircle.security;

import com.carecircle.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

// =============================================================================
// 🧠 JWT STRUCTURE (for interviews):
//
// A JWT has 3 parts separated by dots:
// HEADER.PAYLOAD.SIGNATURE
//
// Header:  {"alg": "HS256", "typ": "JWT"}
// Payload: {"sub": "user-uuid", "email": "...", "role": "ADMIN", "exp": 123...}
// Signature: HMAC-SHA256(base64(header) + "." + base64(payload), secret)
//
// The signature is what makes it tamper-proof. If anyone changes the payload,
// the signature won't match and we reject the token.
//
// 🧠 WHY HttpOnly Cookie instead of localStorage?
// localStorage is accessible by ANY JavaScript on the page.
// XSS attack = attacker injects JS → reads your token → sends it to their server.
// HttpOnly cookie = JavaScript CANNOT read it. Only the browser sends it.
// This is the correct way to store JWTs in 2026.
// =============================================================================

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    // Generate the signing key from our secret string
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    // -------------------------------------------------------------------------
    // GENERATE ACCESS TOKEN (short-lived: 15 minutes)
    // -------------------------------------------------------------------------
    public String generateAccessToken(UUID userId, String email, String role, UUID orgId) {
        return Jwts.builder()
                .subject(userId.toString())           // "sub" claim = user ID
                .claims(Map.of(                        // Custom claims
                        "email", email,
                        "role", role,
                        "orgId", orgId.toString()
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiryMs()))
                .signWith(getSigningKey())
                .compact();
    }

    // -------------------------------------------------------------------------
    // GENERATE REFRESH TOKEN (long-lived: 7 days)
    // -------------------------------------------------------------------------
    // 🧠 Refresh token has MINIMAL claims — just the user ID.
    // It's only used to get a new access token, not to authorize requests.
    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpiryMs()))
                .signWith(getSigningKey())
                .compact();
    }

    // -------------------------------------------------------------------------
    // VALIDATE TOKEN
    // -------------------------------------------------------------------------
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT signature invalid: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT empty: {}", e.getMessage());
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // EXTRACT CLAIMS
    // -------------------------------------------------------------------------
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public UUID extractOrgId(String token) {
        return UUID.fromString(parseClaims(token).get("orgId", String.class));
    }
}