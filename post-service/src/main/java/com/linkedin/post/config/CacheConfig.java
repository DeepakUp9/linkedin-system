package com.linkedin.post.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis cache configuration.
 * 
 * Cache Names:
 * - posts: Individual post cache (TTL: 1 hour)
 * - userPosts: User's posts cache (TTL: 5 minutes)
 * - userFeed: User's feed cache (TTL: 5 minutes)
 * - trendingPosts: Trending posts cache (TTL: 10 minutes)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("posts", defaultConfig.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration("userPosts", defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("userFeed", defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("trendingPosts", defaultConfig.entryTtl(Duration.ofMinutes(10)))
                .build();
    }
}

