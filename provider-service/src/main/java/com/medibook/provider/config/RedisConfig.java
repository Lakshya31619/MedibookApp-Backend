package com.medibook.provider.config;

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
 * Redis configuration for the provider-service.
 *
 * Cache namespaces:
 *   - providers          : Individual provider objects       (TTL 15 min)
 *   - providerSearch     : Search/filter result lists        (TTL 5  min)
 *   - providerStats      : Aggregates like specialization counts (TTL 10 min)
 *
 * FIX: Implements CachingConfigurer to supply a no-op CacheErrorHandler.
 * Without this, a Redis connection failure inside @Cacheable methods
 * (e.g. getSpecializationCounts) throws a RuntimeException which the
 * GlobalExceptionHandler maps to 400 Bad Request, masking the real error.
 * With this handler the service silently falls through to the database.
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
                .entryTtl(Duration.ofMinutes(15))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("providers",      defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigs.put("providerSearch", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("providerStats",  defaultConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    /**
     * FIX: Lenient error handler — logs and swallows Redis errors so that
     * cache failures never propagate as exceptions into controller/handler code.
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