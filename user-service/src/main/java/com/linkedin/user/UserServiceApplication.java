package com.linkedin.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the User Service microservice.
 * 
 * This service handles:
 * - User registration and authentication
 * - Profile management
 * - User search and discovery
 * - Account type management (Basic/Premium)
 * 
 * @SpringBootApplication enables:
 * - Component scanning (finds @Controller, @Service, @Repository)
 * - Auto-configuration (configures beans based on classpath)
 * - Configuration properties (loads application.yml)
 * 
 * @EnableCaching enables Spring's caching abstraction
 * - Supports @Cacheable, @CacheEvict annotations
 * - Backed by Redis
 * 
 * @EnableScheduling enables scheduled tasks
 * - Supports @Scheduled annotation
 * - Used for: Expired token cleanup, periodic jobs
 * 
 * Note: @EnableJpaAuditing is configured in AuditingConfig
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class UserServiceApplication {

    /**
     * Main method - application entry point
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

