package com.medibook.schedule.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
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
 * Redis configuration for the schedule-service.
 *
 * Cache namespaces:
 *   - slots            : Individual slot lookups            (TTL 5 min)
 *   - availableSlots   : Available slot lists per provider  (TTL 2 min — time-sensitive)
 *   - slotsByProvider  : All slots for a provider           (TTL 5 min)
 *
 * Fault-tolerant: if Redis is unreachable at startup, falls back to a no-op
 * cache manager so all requests still hit MySQL without error.
 */
@Configuration
@EnableCaching
public class RedisConfig {

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
        try {
            // Probe the connection — throws immediately if Redis is down
            factory.getConnection().ping();

            RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(5))
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                    .disableCachingNullValues();

            Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
            cacheConfigs.put("slots",           defaultConfig.entryTtl(Duration.ofMinutes(5)));
            cacheConfigs.put("availableSlots",  defaultConfig.entryTtl(Duration.ofMinutes(2)));
            cacheConfigs.put("slotsByProvider", defaultConfig.entryTtl(Duration.ofMinutes(5)));

            return RedisCacheManager.builder(factory)
                    .cacheDefaults(defaultConfig)
                    .withInitialCacheConfigurations(cacheConfigs)
                    .build();

        } catch (Exception e) {
            System.out.println(
                "[RedisConfig] Redis unavailable (" + e.getMessage() + "). " +
                "Falling back to no-op cache — all requests will hit MySQL directly."
            );
            return new NoOpCacheManager();
        }
    }
}