package com.carecircle.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// =============================================================================
// 🧠 AUTH CONTROLLER DESIGN DECISIONS:
//
// 1. Why @CookieValue(required = false)?
//    If the cookie is missing (e.g. user opens /refresh directly in browser),
//    we return a clean 401 JSON — not a 400 MissingRequestCookieException.
//    Better UX than a Spring framework error.
//
// 2. Why does /refresh return 200 with a body?
//    The cookies are set on the HttpServletResponse directly in AuthService.
//    The response body is just a confirmation for the frontend to know
//    the refresh succeeded (it can't read the HttpOnly cookies directly).
//
// 3. Why PUT /logout, not POST?
//    POST is also fine. PUT signals "update user state to logged-out".
//    Either is acceptable in REST — be consistent across your team.
// =============================================================================

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/refresh
    //
    // Called by the frontend when the access token expires (401 response).
    // Browser automatically sends the refresh_token HttpOnly cookie
    // because this endpoint path matches cookie.setPath("/api/v1/auth/refresh").
    // -------------------------------------------------------------------------
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        // Handle missing cookie gracefully — cleaner than Spring's default 400
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("POST /auth/refresh called with no refresh_token cookie");
            return ResponseEntity
                    .status(401)
                    .body(Map.of(
                            "error", "MISSING_REFRESH_TOKEN",
                            "message", "No refresh token cookie found. Please log in again."
                    ));
        }

        // AuthService handles: hash → validate → revoke old → issue new → set cookies
        authService.refresh(refreshToken, response);

        // 🧠 Return 200 so the frontend knows the refresh succeeded.
        // The new access_token and refresh_token are now set as HttpOnly cookies.
        // The frontend cannot read them (that's the security guarantee) — it just
        // knows to retry the original request that got a 401.
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Tokens refreshed successfully"
        ));
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/auth/logout
    //
    // Revokes the refresh token in DB + clears both cookies.
    // The access token remains valid until it expires (it's stateless).
    // 15 minutes is an acceptable window — this is standard JWT behaviour.
    // For stricter logout, add a Redis token blacklist in Sprint 5.
    // -------------------------------------------------------------------------
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken, response);
        } else {
            // Even without a cookie, clear cookies on the browser side
            authService.logout("", response);
        }

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Logged out successfully"
        ));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/auth/me
    //
    // 🧠 This is how Next.js will check if the user is logged in on every
    // page load — it calls /me with credentials:'include', which sends the
    // access_token HttpOnly cookie. Returns the current user's info.
    //
    // The @AuthenticationPrincipal is injected by JwtAuthFilter which ran
    // earlier in the filter chain and set the SecurityContext.
    // -------------------------------------------------------------------------
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.carecircle.domain.user.User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "UNAUTHORIZED",
                    "message", "Not authenticated"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "id", currentUser.getId().toString(),
                "email", currentUser.getEmail(),
                "fullName", currentUser.getFullName(),
                "role", currentUser.getRole().name(),
                "organizationId", currentUser.getOrganization().getId().toString(),
                "avatarUrl", currentUser.getAvatarUrl() != null ? currentUser.getAvatarUrl() : ""
        ));
    }
}