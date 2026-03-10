package com.carecircle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
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

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    // 🧠 FIX: Type parameter is now <String> (not <byte[]>).
    // RateLimitConfig creates LettuceBasedProxyManager<String> using
    // RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE).
    // String keys = human-readable ("rate_limit:userId-abc") in Redis.
    private final LettuceBasedProxyManager<String> rateLimitProxyManager;
    private final ObjectMapper objectMapper;

    private static final int      CAPACITY = 100;
    private static final Duration REFILL   = Duration.ofMinutes(1);

    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/login",
            "/oauth2",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
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

        // String key — readable in redis-cli: KEYS "rate_limit:*"
        String bucketKey = "rate_limit:" + auth.getName();

        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(CAPACITY)
                        .refillGreedy(CAPACITY, REFILL)
                        .build())
                .build();

        Bucket bucket = rateLimitProxyManager.builder()
                .build(bucketKey, () -> config);

        if (bucket.tryConsume(1)) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(CAPACITY));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            response.setHeader("X-RateLimit-Window", "60s");
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for user: {} on path: {}", auth.getName(), path);
            sendRateLimitResponse(response);
        }
    }

    private boolean isExempt(String path) {
        return EXEMPT_PATHS.stream().anyMatch(path::startsWith);
    }

    private void sendRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "error", "RATE_LIMIT_EXCEEDED",
                "message", "Too many requests. Limit: " + CAPACITY + " per minute.",
                "retryAfter", "60 seconds",
                "timestamp", Instant.now().toString()
        )));
    }
}