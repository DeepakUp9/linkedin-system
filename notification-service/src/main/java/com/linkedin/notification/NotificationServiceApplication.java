package com.linkedin.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Notification Service.
 * 
 * Purpose:
 * This microservice handles all notification-related functionality:
 * - Consumes events from Kafka (connection events, post events, etc.)
 * - Sends notifications via multiple channels (in-app, email, push)
 * - Stores notification history
 * - Manages notification preferences
 * 
 * Architecture:
 * - Event-Driven: Listens to Kafka topics for events from other services
 * - Multi-Channel: Delivers notifications via in-app, email, and push
 * - Async: Non-blocking notification delivery
 * - Templated: Uses templates for consistent notification formatting
 * 
 * Enabled Features:
 * - @SpringBootApplication: Auto-configuration and component scanning
 * - @EnableKafka: Enables Kafka consumer listeners
 * - @EnableJpaAuditing: Auto-populates audit fields (createdAt, updatedAt)
 * - @EnableCaching: Redis caching for notification preferences
 * - @EnableFeignClients: REST clients for calling other services (user-service)
 * - @EnableAsync: Async method execution for email sending
 * - @EnableScheduling: Scheduled tasks for cleanup and digests
 * 
 * Design Patterns Used:
 * 1. Observer Pattern: Kafka consumers observe events
 * 2. Strategy Pattern: Different notification delivery strategies
 * 3. Template Method Pattern: Notification templates
 * 4. Factory Pattern: Creating different notification types
 * 
 * Event Flow Example:
 * <pre>
 * Connection Service publishes: "User A accepted connection with User B"
 *   ↓
 * Kafka Topic: "connection-accepted"
 *   ↓
 * Notification Service consumes event
 *   ↓
 * Creates notification: "John Doe accepted your connection request!"
 *   ↓
 * Saves to database (in-app notification)
 *   ↓
 * Sends email to User B
 *   ↓
 * (Optional) Sends push notification
 * </pre>
 * 
 * Database:
 * - PostgreSQL: Stores notifications, preferences, delivery logs
 * - Redis: Caches user preferences and unread counts
 * 
 * Kafka Topics Consumed:
 * - connection-requested
 * - connection-accepted
 * - connection-rejected
 * - post-created (future)
 * - comment-added (future)
 * - job-applied (future)
 * 
 * @see com.linkedin.notification.consumer.ConnectionEventConsumer
 * @see com.linkedin.notification.service.NotificationService
 * @author LinkedIn System
 * @version 1.0
 */
@SpringBootApplication
@EnableKafka
@EnableJpaAuditing
@EnableCaching
@EnableFeignClients
@EnableAsync
@EnableScheduling
public class NotificationServiceApplication {

    /**
     * Main method to start the Notification Service.
     * 
     * This method:
     * 1. Initializes Spring application context
     * 2. Starts embedded Tomcat server (port 8083)
     * 3. Initializes Kafka consumers (starts listening to topics)
     * 4. Sets up email service (SMTP connection)
     * 5. Exposes REST API endpoints
     * 
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    /**
     * Application Startup Information:
     * 
     * On startup, the service:
     * - Connects to PostgreSQL (linkedin_notifications database)
     * - Connects to Redis (for caching)
     * - Connects to Kafka brokers
     * - Subscribes to Kafka topics
     * - Initializes email service (SMTP)
     * - Exposes REST API on port 8083
     * - Swagger UI: http://localhost:8083/swagger-ui.html
     * - Actuator: http://localhost:8083/actuator/health
     */
}

