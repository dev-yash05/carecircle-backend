package com.carecircle.config;

import com.carecircle.domain.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

// =============================================================================
// FIXES APPLIED (see audit report for full details):
//
// Issue 1 — auth.getName() returned User.toString() (Lombok-generated string
//   containing the full object dump). Bucket key was a huge unreadable string
//   in Redis. Fixed: cast principal to User and use user.getId().toString().
//
// Issue 2 — X-RateLimit-Remaining was a race condition. tryConsume(1) and
//   getAvailableTokens() were two separate Redis round-trips. Another request
//   could fire between them, making the header report a stale value.
//   Fixed: use tryConsumeAndReturnRemaining() which is a single atomic CAS
//   operation. Also adds a correct Retry-After header on 429 responses.
//
// Issue 3 — BucketConfiguration was rebuilt on every single request, allocating
//   new objects per call. The config never changes.
//   Fixed: extracted as a static final field, built once at class load time.
//
// Issue 6 — EXEMPT_PATHS was missing /swagger-ui and /v3/api-docs, which are
//   public paths in SecurityConfig. Unauthenticated Swagger users fell through
//   via the anonymousUser check instead of being explicitly skipped.
//   Fixed: added the two missing prefixes for consistency.
// =============================================================================

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final LettuceBasedProxyManager<String> rateLimitProxyManager;
    private final ObjectMapper objectMapper;

    private static final int      CAPACITY = 100;
    private static final Duration REFILL   = Duration.ofMinutes(1);

    // ── FIX (Issue 3): Build config once, not on every request ───────────────
    // BucketConfiguration is immutable and never changes at runtime.
    // static final = allocated once at class load, shared across all requests.
    private static final BucketConfiguration BUCKET_CONFIG = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                    .capacity(CAPACITY)
                    .refillGreedy(CAPACITY, REFILL)
                    .build())
            .build();

    // ── FIX (Issue 6): Exempt paths now match SecurityConfig.permitAll() ─────
    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/login",
            "/oauth2",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/swagger-ui",        // added — matches SecurityConfig permitAll
            "/v3/api-docs"        // added — matches SecurityConfig permitAll
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (isExempt(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── FIX (Issue 1): Use user UUID as the bucket key ───────────────────
        // JwtAuthFilter sets the principal as a User object, not a String.
        // Calling auth.getName() on a UsernamePasswordAuthenticationToken whose
        // principal is a User invokes principal.toString() — Lombok generates a
        // huge string like "User(id=abc, email=..., fullName=..., role=...)".
        // That entire dump became the Redis key, which is:
        //   - Unreadable in redis-cli
        //   - Potentially hundreds of bytes per key
        //   - Different on every toString() if field order ever changes
        // Correct key: "rate_limit:<uuid>" — stable, short, debuggable.
        String bucketKey;
        if (auth.getPrincipal() instanceof User user) {
            bucketKey = "rate_limit:" + user.getId().toString();
        } else {
            // Fallback for any non-User principal (e.g. tests, service accounts)
            bucketKey = "rate_limit:" + auth.getName();
        }

        // ── FIX (Issue 2): Atomic probe — one Redis round-trip ───────────────
        // Old code:
        //   bucket.tryConsume(1)          ← Redis CAS round-trip #1
        //   bucket.getAvailableTokens()   ← Redis CAS round-trip #2
        // Between those two calls, another request from the same user could
        // fire and consume more tokens — the Remaining header would lie.
        //
        // tryConsumeAndReturnRemaining() performs a single atomic CAS:
        // it consumes the token AND reads the remaining count in one operation.
        // ConsumptionProbe.getRemainingTokens() is the accurate post-consume value.
        ConsumptionProbe probe = rateLimitProxyManager
                .builder()
                .build(bucketKey, () -> BUCKET_CONFIG)
                .tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Limit",     String.valueOf(CAPACITY));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            response.setHeader("X-RateLimit-Window",    "60s");
            filterChain.doFilter(request, response);
        } else {
            // nanosToWaitForRefill is exact — how long until the bucket has 1 token again
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

            log.warn("Rate limit exceeded — bucketKey: {}, path: {}, retryAfter: {}s",
                    bucketKey, path, retryAfterSeconds);

            sendRateLimitResponse(response, retryAfterSeconds);
        }
    }

    private boolean isExempt(String path) {
        return EXEMPT_PATHS.stream().anyMatch(path::startsWith);
    }

    private void sendRateLimitResponse(HttpServletResponse response,
                                       long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "error",       "RATE_LIMIT_EXCEEDED",
                "message",     "Too many requests. Limit: " + CAPACITY + " per minute.",
                "retryAfter",  retryAfterSeconds + " seconds",
                "timestamp",   Instant.now().toString()
        )));
    }
}