package com.linkedin.connection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Connection Service.
 * 
 * This microservice manages professional connections between users, including:
 * - Sending and accepting connection requests
 * - Managing connection state (PENDING, ACCEPTED, REJECTED)
 * - Providing connection suggestions
 * - Publishing connection events to Kafka
 * - Communicating with User Service via Feign
 * 
 * Enabled Features:
 * - {@code @SpringBootApplication}: Auto-configuration, component scanning, and configuration
 * - {@code @EnableCaching}: Redis-based caching for connection data and suggestions
 * - {@code @EnableFeignClients}: Declarative REST clients for inter-service communication
 * - {@code @EnableScheduling}: Scheduled tasks for cleanup and maintenance
 * 
 * Note: @EnableJpaAuditing is configured in AuditingConfig class with auditorProvider bean
 * 
 * Design Patterns Applied in This Service:
 * 1. **State Pattern**: Managing connection state transitions (ConnectionStateHandler)
 * 2. **Strategy Pattern**: Different algorithms for connection suggestions (SuggestionStrategy)
 * 3. **Repository Pattern**: Data access abstraction (ConnectionRepository)
 * 4. **DTO Pattern**: Request/Response data transfer objects
 * 5. **Observer Pattern** (via Kafka): Event-driven communication with other services
 * 
 * Microservices Integration:
 * - **User Service**: Validates user existence before creating connections (via Feign)
 * - **Notification Service**: Receives connection events to send notifications (via Kafka)
 * - **API Gateway**: Routes external requests to this service
 * - **Config Server**: Centralized configuration management
 * - **Service Registry**: Service discovery with Eureka
 * 
 * Database:
 * - PostgreSQL: Stores connection data (separate database: linkedin_connections)
 * - Flyway: Version-controlled database migrations
 * - Redis: Caches connection lists, mutual connections, and suggestions
 * 
 * Security:
 * - JWT-based authentication (reuses security configuration from user-service)
 * - Authorization: Users can only manage their own connections
 * 
 * @see com.linkedin.connection.model.Connection
 * @see com.linkedin.connection.service.ConnectionService
 * @see com.linkedin.connection.patterns.state.ConnectionStateHandler
 * @see com.linkedin.connection.patterns.strategy.SuggestionStrategy
 */
@SpringBootApplication
@EnableCaching
@EnableFeignClients
@EnableScheduling
public class ConnectionServiceApplication {

    /**
     * Main method to start the Connection Service.
     * 
     * This method:
     * 1. Initializes the Spring application context
     * 2. Starts the embedded Tomcat server (default port: 8082)
     * 3. Scans for components in the com.linkedin.connection package
     * 4. Configures data source, JPA, Redis, Kafka, etc.
     * 5. Exposes REST endpoints via ConnectionController
     * 
     * @param args Command-line arguments (optional)
     */
    public static void main(String[] args) {
        SpringApplication.run(ConnectionServiceApplication.class, args);
    }

    /**
     * Application startup banner information.
     * 
     * When the application starts, Spring Boot displays:
     * - Service name: Connection Service
     * - Spring Boot version
     * - Active profiles (dev, prod, etc.)
     * - Running on port: 8082 (configurable in application.yml)
     * - Available endpoints: /api/connections/**
     * - Swagger UI: http://localhost:8082/swagger-ui.html
     * - Actuator: http://localhost:8082/actuator/health
     */
}

