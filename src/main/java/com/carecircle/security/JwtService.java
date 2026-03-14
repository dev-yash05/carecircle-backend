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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    // -------------------------------------------------------------------------
    // GENERATE ACCESS TOKEN
    // 🧠 orgId is nullable — SUPER_ADMIN has no organization.
    //    We store "SUPER_ADMIN" as a literal string in the orgId claim
    //    so the frontend can check it without a null-pointer crash.
    // -------------------------------------------------------------------------
    public String generateAccessToken(UUID userId, String email, String role, UUID orgId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("role", role);
        // Use "SUPER_ADMIN" as a sentinel value when there's no real orgId.
        // The frontend and JwtAuthFilter both check role first, so this
        // string will never be parsed as a UUID for SUPER_ADMIN users.
        claims.put("orgId", orgId != null ? orgId.toString() : "SUPER_ADMIN");

        return Jwts.builder()
                .subject(userId.toString())
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiryMs()))
                .signWith(getSigningKey())
                .compact();
    }

    // Refresh token — minimal claims, just the user ID
    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpiryMs()))
                .signWith(getSigningKey())
                .compact();
    }

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

    // 🧠 Returns null for SUPER_ADMIN (their orgId claim is the sentinel string).
    // Callers should check role first before calling this.
    public UUID extractOrgId(String token) {
        String raw = parseClaims(token).get("orgId", String.class);
        if (raw == null || raw.equals("SUPER_ADMIN")) return null;
        return UUID.fromString(raw);
    }
}