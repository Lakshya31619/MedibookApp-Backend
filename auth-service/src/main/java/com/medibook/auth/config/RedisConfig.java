package com.medibook.auth.config;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis configuration for the auth-service.
 *
 * Cache namespaces:
 *   - users           : User objects by email / userId  (TTL 30 min)
 *   - tokenValidation : JWT validation results           (TTL 10 min)
 *   - verificationCodes: email OTP codes                (TTL 10 min)
 *
 * FIX: Implements CachingConfigurer to supply a no-op CacheErrorHandler.
 * This prevents Redis connection errors (e.g. Redis not running in dev)
 * from propagating as RuntimeExceptions and causing misleading 404/400
 * responses. On a cache miss/error the service falls back to the database.
 */
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        Jackson2JsonRedisSerializer<Object> jsonSerializer =
                new Jackson2JsonRedisSerializer<>(Object.class);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("users",             defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("tokenValidation",   defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("verificationCodes", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    /**
     * FIX: Return a lenient CacheErrorHandler that logs and swallows all Redis
     * errors instead of rethrowing them.  Without this, a Redis connection
     * failure on @Cacheable methods throws a RuntimeException which the
     * GlobalExceptionHandler (or the getProfile try/catch) maps to 400 or 404.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                System.err.println("[Cache] GET error on cache='" + cache.getName()
                        + "' key='" + key + "': " + e.getMessage() + " — falling back to DB");
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                System.err.println("[Cache] PUT error on cache='" + cache.getName()
                        + "' key='" + key + "': " + e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                System.err.println("[Cache] EVICT error on cache='" + cache.getName()
                        + "' key='" + key + "': " + e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                System.err.println("[Cache] CLEAR error on cache='" + cache.getName()
                        + "': " + e.getMessage());
            }
        };
    }
}