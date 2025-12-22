package com.linkedin.notification.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache configuration for Notification Service.
 * 
 * Purpose:
 * - Configure Redis as cache provider
 * - Define cache regions with specific TTLs
 * - Configure JSON serialization for cached objects
 * 
 * Cache Regions:
 * 1. userNotifications: Paginated notifications (TTL: 5 minutes)
 * 2. unreadNotifications: Unread notifications list (TTL: 5 minutes)
 * 3. notificationStats: Stats (unread count, etc.) (TTL: 5 minutes)
 * 4. userPreferences: User preferences (TTL: 1 hour)
 * 5. userPreference: Single preference (TTL: 1 hour)
 * 
 * How Caching Works:
 * <pre>
 * First Call:
 *   @Cacheable("unreadNotifications")
 *   public List<Notification> getUnread(Long userId) {
 *       // Query database (slow)
 *       return notifications;
 *   }
 *   → Stores in Redis with key "unreadNotifications::123"
 * 
 * Subsequent Calls:
 *   → Returns from Redis (fast!) ⚡
 *   → No database query
 * 
 * Cache Invalidation:
 *   @CacheEvict("unreadNotifications")
 *   public void markAsRead(Long id) {
 *       // Update database
 *   }
 *   → Removes from Redis
 *   → Next call will refresh from database
 * </pre>
 * 
 * Performance Impact:
 * - Database queries: ~50-200ms
 * - Redis cache hits: ~1-5ms
 * - 10-50x faster! 🚀
 * 
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.CacheEvict
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure ObjectMapper for Redis serialization.
     * 
     * Features:
     * - JavaTimeModule: Serialize LocalDateTime, etc.
     * - LaissezFaireSubTypeValidator: Handle polymorphic types
     * - Visibility: Serialize all fields
     * 
     * @return Configured ObjectMapper
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL
        );
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    /**
     * Configure RedisCacheManager with custom cache configurations.
     * 
     * Each cache region can have different:
     * - TTL (time-to-live)
     * - Serialization settings
     * - Null value handling
     * 
     * @param redisConnectionFactory Redis connection factory
     * @param redisObjectMapper ObjectMapper for serialization
     * @return Configured RedisCacheManager
     */
    @Bean
    public CacheManager cacheManager(
        RedisConnectionFactory redisConnectionFactory,
        ObjectMapper redisObjectMapper
    ) {
        // Default configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new Jackson2JsonRedisSerializer<>(redisObjectMapper, Object.class)
                )
            );

        // Custom configurations for specific caches
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Notifications (5 minutes)
        cacheConfigurations.put("userNotifications", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("unreadNotifications", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("notificationStats", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Preferences (1 hour - changes less frequently)
        cacheConfigurations.put("userPreferences", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("userPreference", defaultConfig.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}

