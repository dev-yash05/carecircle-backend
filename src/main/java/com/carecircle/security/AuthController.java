package com.carecircle.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST /api/v1/auth/refresh
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).body(Map.of(
                    "error",   "MISSING_REFRESH_TOKEN",
                    "message", "No refresh token cookie found. Please log in again."
            ));
        }
        authService.refresh(refreshToken, response);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Tokens refreshed successfully"));
    }

    // POST /api/v1/auth/logout
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken != null ? refreshToken : "", response);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Logged out successfully"));
    }

    // GET /api/v1/auth/me
    // 🧠 SUPER_ADMIN has no organization — the organizationId field is omitted
    // from the response rather than crashing with a NullPointerException.
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @AuthenticationPrincipal com.carecircle.domain.user.User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error",   "UNAUTHORIZED",
                    "message", "Not authenticated"
            ));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("id",       currentUser.getId().toString());
        body.put("email",    currentUser.getEmail());
        body.put("fullName", currentUser.getFullName());
        body.put("role",     currentUser.getRole().name());
        body.put("avatarUrl", currentUser.getAvatarUrl() != null ? currentUser.getAvatarUrl() : "");

        // SUPER_ADMIN has no org — don't include the field so the frontend
        // can check: if (me.organizationId) { ... }
        if (currentUser.getOrganization() != null) {
            body.put("organizationId", currentUser.getOrganization().getId().toString());
        }

        return ResponseEntity.ok(body);
    }
}