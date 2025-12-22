package com.linkedin.post;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Post Service.
 * 
 * Purpose:
 * This microservice handles all post-related functionality:
 * - Creating, editing, deleting posts
 * - Liking and unliking posts
 * - Commenting on posts (with nested replies)
 * - Sharing/reposting content
 * - Generating personalized feed
 * - Tracking post statistics
 * - Managing hashtags
 * 
 * Architecture:
 * - RESTful API for frontend
 * - PostgreSQL for data persistence
 * - Redis for caching popular posts
 * - Kafka for event publishing
 * - Feign clients for inter-service communication
 * 
 * Enabled Features:
 * - @SpringBootApplication: Auto-configuration and component scanning
 * - @EnableJpaAuditing: Auto-populates audit fields (createdAt, updatedAt)
 * - @EnableCaching: Redis caching for posts and feed
 * - @EnableFeignClients: REST clients for calling other services
 * - @EnableKafka: Publishes events for notifications
 * - @EnableAsync: Async processing for heavy operations
 * - @EnableScheduling: Scheduled tasks for trending hashtags
 * 
 * Design Patterns Used:
 * 1. Repository Pattern: Data access abstraction
 * 2. Strategy Pattern: Post visibility rules
 * 3. Observer Pattern: Kafka events
 * 4. Factory Pattern: Post creation
 * 5. Composite Pattern: Nested comments
 * 
 * Event Flow Example:
 * <pre>
 * User likes a post:
 *   ↓
 * POST /api/posts/{id}/like
 *   ↓
 * PostService.likePost()
 *   ↓
 * Saves Like entity to database
 *   ↓
 * Publishes PostLikedEvent to Kafka
 *   ↓
 * Notification Service receives event
 *   ↓
 * Creates notification: "John liked your post"
 *   ↓
 * Post author receives notification ✅
 * </pre>
 * 
 * Database:
 * - PostgreSQL: Stores posts, comments, likes
 * - Redis: Caches popular posts, user feeds
 * 
 * Kafka Topics Published:
 * - post-created: New post published
 * - post-liked: Someone liked a post
 * - post-commented: Someone commented on a post
 * - post-shared: Someone shared a post
 * - post-deleted: Post was deleted
 * 
 * REST API Endpoints:
 * - POST /api/posts: Create post
 * - GET /api/posts/{id}: View post
 * - PUT /api/posts/{id}: Edit post
 * - DELETE /api/posts/{id}: Delete post
 * - POST /api/posts/{id}/like: Like post
 * - DELETE /api/posts/{id}/like: Unlike post
 * - POST /api/posts/{id}/comments: Add comment
 * - GET /api/posts/{id}/comments: List comments
 * - POST /api/posts/{id}/share: Share post
 * - GET /api/feed: Get personalized feed
 * - GET /api/posts/trending: Get trending posts
 * 
 * @see com.linkedin.post.controller.PostController
 * @see com.linkedin.post.service.PostService
 * @see com.linkedin.post.service.FeedService
 * @author LinkedIn System
 * @version 1.0
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableFeignClients
@EnableKafka
@EnableAsync
@EnableScheduling
public class PostServiceApplication {

    /**
     * Main method to start the Post Service.
     * 
     * This method:
     * 1. Initializes Spring application context
     * 2. Starts embedded Tomcat server (port 8084)
     * 3. Connects to PostgreSQL database (linkedin_posts)
     * 4. Connects to Redis for caching
     * 5. Connects to Kafka brokers
     * 6. Exposes REST API endpoints
     * 
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(PostServiceApplication.class, args);
    }

    /**
     * Application Startup Information:
     * 
     * On startup, the service:
     * - Connects to PostgreSQL (linkedin_posts database)
     * - Connects to Redis (for caching)
     * - Connects to Kafka brokers (for event publishing)
     * - Initializes Feign clients (user-service, connection-service)
     * - Exposes REST API on port 8084
     * - Swagger UI: http://localhost:8084/swagger-ui.html
     * - Actuator: http://localhost:8084/actuator/health
     * 
     * Configuration:
     * - Database: See application.yml (spring.datasource)
     * - Redis: See application.yml (spring.data.redis)
     * - Kafka: See application.yml (spring.kafka)
     * - Feign: See application.yml (feign.client)
     */
}

