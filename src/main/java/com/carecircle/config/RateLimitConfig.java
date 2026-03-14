package com.carecircle.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// =============================================================================
// 🧠 BUCKET4J 8.10.1 — CORRECT API (verified against official docs)
//
// The API changed significantly across versions:
//
//   8.10.1 (our version):
//     LettuceBasedProxyManager.builderFor(connection)
//       .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(...))
//       .build()
//
//   8.12+ (newer API, NOT available in 8.10.1):
//     Bucket4jLettuce.casBasedBuilder(connection)      ← this class DOES NOT EXIST in 8.10.1
//       .expirationAfterWrite(...)
//       .build()
//
// FIX (Issue 4) — RedisClient lifecycle:
//   Previously: RedisClient was a local variable inside the @Bean method.
//   Spring returned the connection but the RedisClient that owns it had no
//   managed lifecycle — it was never shut down, leaking TCP connections.
//
//   Now: RedisClient is its own @Bean with destroyMethod="shutdown".
//   Spring calls redisClient.shutdown() on context close, which gracefully
//   closes all connections before the JVM exits.
//
//   destroyMethod="close" on the connection ensures it is closed first,
//   then the client shuts down — correct teardown order.
// =============================================================================

@Configuration
public class RateLimitConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    // ── FIX (Issue 4): RedisClient as a managed bean ─────────────────────────
    // destroyMethod="shutdown" → Spring calls redisClient.shutdown() on
    // application context close. This sends a QUIT to Redis, closes the TCP
    // socket, and releases the Netty event loop threads cleanly.
    @Bean(destroyMethod = "shutdown")
    public RedisClient bucket4jRedisClient() {
        String redisUri = redisPassword.isEmpty()
                ? String.format("redis://%s:%d", redisHost, redisPort)
                : String.format("redis://:%s@%s:%d", redisPassword, redisHost, redisPort);
        return RedisClient.create(redisUri);
    }

    // destroyMethod="close" → connection is closed before the client shuts down.
    // Correct teardown order: close connection first, then shutdown client.
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> redisConnectionForBucket4j(
            RedisClient bucket4jRedisClient) {
        // String keys (human-readable: "rate_limit:uuid") + byte[] values (bucket state).
        // This codec is required by Bucket4j — cannot share with Spring's StringRedisTemplate.
        return bucket4jRedisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Bean
    public LettuceBasedProxyManager<String> rateLimitProxyManager(
            StatefulRedisConnection<String, byte[]> redisConnectionForBucket4j) {

        // 🧠 Official 8.10.1 builder API — builderFor(), not Bucket4jLettuce.casBasedBuilder()
        return LettuceBasedProxyManager.builderFor(redisConnectionForBucket4j)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                Duration.ofMinutes(2)
                        )
                )
                .build();
    }
}