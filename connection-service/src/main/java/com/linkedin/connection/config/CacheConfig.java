package com.linkedin.connection.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for Redis caching.
 * 
 * Purpose:
 * Configures Redis as the caching provider for the connection-service.
 * Defines cache names, TTL (Time-To-Live), and serialization strategies.
 * 
 * What It Does:
 * 1. Enables Spring's caching abstraction (@EnableCaching)
 * 2. Configures Redis as the cache store
 * 3. Sets up JSON serialization for cached objects
 * 4. Defines cache-specific TTL policies
 * 
 * How It Works:
 * <pre>
 * Service Method with @Cacheable:
 * 
 * {@code
 * @Cacheable(value = "userConnections", key = "#userId")
 * public List<ConnectionResponseDto> getMyConnections(Long userId) {
 *     // This code only runs on cache miss
 *     return connectionRepository.findByUserIdAndState(userId, ACCEPTED);
 * }
 * }
 * 
 * Flow:
 * 1st Call: getMyConnections(123)
 *   → Cache miss → Queries database → Stores in Redis → Returns result
 * 
 * 2nd Call: getMyConnections(123) [within 1 hour]
 *   → Cache hit → Returns from Redis (no DB query!)
 * 
 * After 1 hour:
 *   → Cache expired → Queries database again → Refreshes cache
 * </pre>
 * 
 * Cache Definitions:
 * 
 * 1. **userConnections** (TTL: 1 hour)
 *    - Stores a user's connection list
 *    - Evicted when connections change (accept, remove, etc.)
 *    - Key format: userId (e.g., "123")
 * 
 * 2. **connectionCounts** (TTL: 1 hour)
 *    - Stores connection count for a user
 *    - Evicted when connections change
 *    - Key format: userId
 * 
 * 3. **connections** (TTL: 30 minutes)
 *    - Stores individual connection details
 *    - Key format: connectionId
 * 
 * 4. **connectionSuggestions** (TTL: 6 hours)
 *    - Stores "People You May Know" suggestions
 *    - Expensive to compute, so longer TTL
 *    - Key format: userId_limit (e.g., "123_10")
 * 
 * Cache Eviction:
 * <pre>
 * {@code
 * @CacheEvict(value = "userConnections", key = "#userId")
 * public void acceptConnection(Long connectionId, Long userId) {
 *     // Accept connection
 *     // Cache is automatically cleared for this user
 * }
 * }
 * </pre>
 * 
 * Serialization Strategy:
 * - Keys: StringRedisSerializer (simple strings)
 * - Values: JSON (GenericJackson2JsonRedisSerializer)
 * - Includes type information for polymorphic deserialization
 * - Supports LocalDateTime, LocalDate, etc. (JavaTimeModule)
 * 
 * Example Redis Storage:
 * <pre>
 * Key: userConnections::123
 * Value: [
 *   {
 *     "@class": "com.linkedin.connection.dto.ConnectionResponseDto",
 *     "id": 1,
 *     "requester": {...},
 *     "addressee": {...},
 *     "state": "ACCEPTED",
 *     "createdAt": "2025-12-20T10:30:00"
 *   },
 *   ...
 * ]
 * TTL: 3600 seconds
 * </pre>
 * 
 * Benefits:
 * - Reduces database load (especially for frequently accessed data)
 * - Improves response time (sub-millisecond cache hits)
 * - Scales horizontally (Redis cluster)
 * - Automatic expiration (no stale data beyond TTL)
 * 
 * Performance Impact:
 * - Database query: 10-50ms
 * - Redis cache hit: 1-5ms
 * - 10x-50x faster response time
 * 
 * Monitoring:
 * - Cache hit/miss ratio (should be > 80% for effective caching)
 * - Cache size (monitor memory usage)
 * - Eviction count (should be low, indicates proper TTL)
 * 
 * Related Annotations:
 * @see org.springframework.cache.annotation.Cacheable
 * @see org.springframework.cache.annotation.CacheEvict
 * @see org.springframework.cache.annotation.CachePut
 * @see com.linkedin.connection.service.ConnectionService
 * 
 * @author LinkedIn System
 * @version 1.0
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configures the Redis Cache Manager with custom TTL for different caches.
     * 
     * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
     * @return Configured CacheManager
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Create ObjectMapper for JSON serialization
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Register JavaTimeModule for LocalDateTime, LocalDate, etc.
        objectMapper.registerModule(new JavaTimeModule());
        
        // Enable default typing for polymorphic deserialization
        // This includes @class field in JSON to preserve type information
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );

        // Create JSON serializer
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);

        // Default cache configuration
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)) // Default TTL: 1 hour
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
                )
                .disableCachingNullValues(); // Don't cache null values

        // Custom cache configurations with specific TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 1. User Connections Cache (1 hour)
        // Stores list of connections for a user
        cacheConfigurations.put("userConnections", 
            defaultCacheConfig.entryTtl(Duration.ofHours(1)));

        // 2. Connection Counts Cache (1 hour)
        // Stores count of connections for a user
        cacheConfigurations.put("connectionCounts", 
            defaultCacheConfig.entryTtl(Duration.ofHours(1)));

        // 3. Individual Connection Cache (30 minutes)
        // Stores details of a single connection
        cacheConfigurations.put("connections", 
            defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));

        // 4. Connection Suggestions Cache (6 hours)
        // "People You May Know" - expensive to compute, so longer TTL
        cacheConfigurations.put("connectionSuggestions", 
            defaultCacheConfig.entryTtl(Duration.ofHours(6)));

        // 5. Mutual Connections Cache (2 hours)
        // Stores mutual connections between two users
        cacheConfigurations.put("mutualConnections", 
            defaultCacheConfig.entryTtl(Duration.ofHours(2)));

        // 6. User Profile Cache (from user-service calls via Feign)
        // Cache user details to reduce inter-service calls
        cacheConfigurations.put("userProfiles", 
            defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));

        // Build and return the cache manager
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware() // Enable cache operations within transactions
                .build();
    }

    /**
     * Cache Statistics (for monitoring and debugging).
     * 
     * To enable cache statistics in production:
     * 1. Add Spring Boot Actuator dependency
     * 2. Enable cache metrics: management.metrics.enable.cache=true
     * 3. Access metrics: /actuator/metrics/cache.size
     * 
     * Example metrics:
     * - cache.gets (total cache get operations)
     * - cache.puts (total cache put operations)
     * - cache.evictions (total cache evictions)
     * - cache.hit.ratio (hit rate: should be > 0.8)
     */

    /**
     * Cache Warming Strategy (optional).
     * 
     * For critical data that should always be cached, you can:
     * 1. Pre-load cache on application startup
     * 2. Use scheduled tasks to refresh cache before expiration
     * 
     * Example:
     * <pre>
     * {@code
     * @Component
     * public class CacheWarmer {
     *     @Autowired
     *     private ConnectionService connectionService;
     *     
     *     @EventListener(ApplicationReadyEvent.class)
     *     public void warmCache() {
     *         // Pre-load popular users' connections
     *         List<Long> popularUserIds = getPopularUsers();
     *         for (Long userId : popularUserIds) {
     *             connectionService.getMyConnections(userId);
     *         }
     *     }
     * }
     * }
     * </pre>
     */

    /**
     * Cache Key Generation Strategy.
     * 
     * Spring's default key generator uses:
     * - No parameters: SimpleKey.EMPTY
     * - Single parameter: The parameter itself
     * - Multiple parameters: SimpleKey(param1, param2, ...)
     * 
     * Custom key generation:
     * <pre>
     * {@code
     * @Cacheable(value = "connections", key = "#userId + '_' + #connectionId")
     * public Connection getConnection(Long userId, Long connectionId) { ... }
     * }
     * </pre>
     * 
     * Key format in Redis: cacheName::key
     * Example: "userConnections::123"
     */
}

