package com.carecircle.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

// =============================================================================
// 🧠 WHY A CUSTOM CacheManager INSTEAD OF application.yaml defaults?
//
// application.yaml sets one global TTL (300s) for ALL caches.
// But different caches have different freshness requirements:
//
//   dose_events   → changes when a caregiver marks a dose. 5 min TTL is fine.
//   schedules     → rarely changes (admin creates them). 60 min is safe.
//
// Named caches let us tune TTL per use-case without a single global config.
//
// Also: We configure JSON serialization explicitly.
// Default Redis serializer uses Java's native serializer which:
//   - Stores unreadable binary blobs in Redis
//   - Breaks if you rename a class
// JSON is human-readable with redis-cli and survives class refactors.
// =============================================================================

@Configuration
@EnableCaching   // Activates @Cacheable / @CacheEvict / @CachePut scanning
public class CacheConfig {

    // Cache name constants — used in @Cacheable annotations.
    // Always define as constants, never type string literals in annotations.
    // A typo in a string = silent cache miss, very hard to debug.
    public static final String DOSE_EVENTS_CACHE   = "dose_events";
    public static final String MED_SCHEDULES_CACHE = "med_schedules";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {

        // Base config — applied to all caches unless overridden
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // 🧠 Serialize values as JSON (not Java binary)
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                )
                // Serialize keys as plain strings (e.g. "dose_events::orgId:patientId:date")
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )
                // 🧠 Don't cache null results — if the DB returns null (e.g. patient not found),
                // we don't want to cache that. Next request should hit DB and maybe find it.
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(5));   // Default: 5 minutes

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(

                // Dose events: invalidated by markDose() anyway — 5 min is generous
                DOSE_EVENTS_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(5)),

                // Medication schedules: rarely change — cache for 1 hour
                // CacheEvict on createSchedule() handles invalidation
                MED_SCHEDULES_CACHE, defaultConfig.entryTtl(Duration.ofHours(1))
        );

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}