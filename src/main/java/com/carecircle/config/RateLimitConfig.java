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
// Lesson: always verify the API against the EXACT version in pom.xml.
// Mixing API docs from newer versions is a common source of "cannot find symbol" errors.
//
// CONNECTION CODEC:
// The connection uses RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
// — String keys (human-readable: "rate_limit:userId") + byte[] values (bucket state).
// This is exactly what the official 8.10.1 reference example shows.
// =============================================================================

@Configuration
public class RateLimitConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public StatefulRedisConnection<String, byte[]> redisConnectionForBucket4j() {
        RedisClient redisClient = RedisClient.create("redis://" + redisHost + ":" + redisPort);
        // String keys (for readability in redis-cli) + byte[] values (Bucket4j's binary state)
        return redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
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