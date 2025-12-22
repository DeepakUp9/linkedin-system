package com.linkedin.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Configuration for JPA Auditing.
 * 
 * This class enables Spring Data JPA's auditing feature, which automatically
 * populates audit fields (@CreatedDate, @LastModifiedDate, @CreatedBy, @LastModifiedBy)
 * in entities that extend BaseAuditEntity.
 * 
 * Key Components:
 * 
 * 1. @EnableJpaAuditing:
 *    - Activates JPA auditing in Spring Data JPA
 *    - Tells Spring to look for @EntityListeners(AuditingEntityListener.class)
 *    - Enables @CreatedDate and @LastModifiedDate annotations
 * 
 * 2. AuditorAware Bean:
 *    - Provides the current user's identity for @CreatedBy and @LastModifiedBy
 *    - Spring calls this bean whenever an entity is created or modified
 *    - Returns the username from Spring Security context
 * 
 * How It Works:
 * 
 * Without Auditing (Manual):
 * <pre>
 * {@code
 * User user = new User();
 * user.setEmail("john@example.com");
 * user.setCreatedAt(LocalDateTime.now());        // Manual!
 * user.setUpdatedAt(LocalDateTime.now());        // Manual!
 * user.setCreatedBy("current-user");             // Manual!
 * user.setUpdatedBy("current-user");             // Manual!
 * userRepository.save(user);
 * }
 * </pre>
 * 
 * With Auditing (Automatic):
 * <pre>
 * {@code
 * User user = new User();
 * user.setEmail("john@example.com");
 * // createdAt, updatedAt, createdBy, updatedBy are set automatically!
 * userRepository.save(user);
 * }
 * </pre>
 * 
 * Auditing Flow:
 * 
 * 1. Application starts:
 *    - Spring sees @EnableJpaAuditing
 *    - Registers AuditingEntityListener
 *    - Finds AuditorAware bean
 * 
 * 2. Entity is saved (INSERT):
 *    - AuditingEntityListener intercepts the save operation
 *    - Calls getCurrentAuditor() to get current user
 *    - Sets createdAt = LocalDateTime.now()
 *    - Sets updatedAt = LocalDateTime.now()
 *    - Sets createdBy = current username
 *    - Sets updatedBy = current username
 * 
 * 3. Entity is updated (UPDATE):
 *    - AuditingEntityListener intercepts the update operation
 *    - Calls getCurrentAuditor() to get current user
 *    - Sets updatedAt = LocalDateTime.now() (createdAt unchanged)
 *    - Sets updatedBy = current username (createdBy unchanged)
 * 
 * Benefits:
 * 
 * 1. Consistency:
 *    - All entities have accurate audit information
 *    - No manual errors (forgot to set updatedAt)
 * 
 * 2. Compliance:
 *    - Audit trails for regulatory requirements (GDPR, SOC2, HIPAA)
 *    - Track who created/modified each record
 * 
 * 3. Debugging:
 *    - Trace when data was created/modified
 *    - Identify who made changes
 * 
 * 4. Business Logic:
 *    - "Show posts created in last 24 hours"
 *    - "Find users created by admin"
 * 
 * Security Context Integration:
 * 
 * When a user makes an authenticated request:
 * 1. Spring Security authenticates user (JWT/OAuth2)
 * 2. Stores Authentication in SecurityContextHolder
 * 3. Our AuditorAware reads from SecurityContextHolder
 * 4. Returns username for audit fields
 * 
 * Example Flow:
 * <pre>
 * {@code
 * // User "john@example.com" is authenticated
 * SecurityContext: Authentication(principal="john@example.com")
 * 
 * // User creates a new post
 * POST /api/posts
 * 
 * // Service saves post entity
 * Post post = new Post();
 * post.setTitle("Hello World");
 * postRepository.save(post);
 * 
 * // AuditingEntityListener runs:
 * getCurrentAuditor() → "john@example.com"
 * post.setCreatedBy("john@example.com");
 * post.setUpdatedBy("john@example.com");
 * 
 * // Database record:
 * // title: "Hello World"
 * // created_by: "john@example.com"
 * // updated_by: "john@example.com"
 * // created_at: 2025-12-20T17:30:00
 * // updated_at: 2025-12-20T17:30:00
 * }
 * </pre>
 * 
 * Handling Unauthenticated Requests:
 * 
 * Some operations don't have authenticated users:
 * - System background jobs
 * - Database migrations
 * - Initial data seeding
 * - Public registration endpoints
 * 
 * In these cases:
 * - SecurityContextHolder.getContext().getAuthentication() returns null
 * - getCurrentAuditor() returns Optional.empty()
 * - Spring leaves createdBy/updatedBy as null (or you can return "SYSTEM")
 * 
 * @see BaseAuditEntity
 * @see org.springframework.data.jpa.domain.support.AuditingEntityListener
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditingConfig {

    /**
     * Provides the current auditor (user) for JPA auditing.
     * 
     * This bean is called by Spring Data JPA whenever an entity with @CreatedBy
     * or @LastModifiedBy is saved or updated.
     * 
     * Implementation Details:
     * 
     * 1. Gets the current Spring Security authentication
     * 2. If authenticated, extracts the username (principal)
     * 3. Returns username wrapped in Optional
     * 4. If not authenticated, returns Optional.empty()
     * 
     * Authentication States:
     * 
     * Authenticated Request:
     * - User logged in via JWT/OAuth2
     * - SecurityContext contains Authentication object
     * - Principal is username/email
     * - Returns: Optional.of("john@example.com")
     * 
     * Unauthenticated Request:
     * - Public endpoint (e.g., user registration)
     * - System job (e.g., scheduled cleanup)
     * - SecurityContext is empty or anonymous
     * - Returns: Optional.empty() → createdBy/updatedBy = null
     * 
     * Security Context Structure:
     * <pre>
     * {@code
     * SecurityContextHolder
     *   └─ SecurityContext
     *        └─ Authentication
     *             ├─ Principal: "john@example.com" (username)
     *             ├─ Credentials: [PROTECTED]
     *             ├─ Authorities: [ROLE_USER, ROLE_PREMIUM]
     *             └─ Authenticated: true
     * }
     * </pre>
     * 
     * Example Scenarios:
     * 
     * Scenario 1: Authenticated User Creates Entity
     * <pre>
     * {@code
     * // User "jane@example.com" is logged in
     * POST /api/users/profile-update
     * 
     * // Inside service:
     * user.setName("Jane Smith");
     * userRepository.save(user);
     * 
     * // This method is called:
     * getCurrentAuditor() → Optional.of("jane@example.com")
     * 
     * // Database:
     * // updated_by: "jane@example.com"
     * // updated_at: 2025-12-20T17:30:00
     * }
     * </pre>
     * 
     * Scenario 2: New User Registration (Unauthenticated)
     * <pre>
     * {@code
     * // No user logged in (public registration)
     * POST /api/users/register
     * 
     * // Inside service:
     * User user = new User();
     * user.setEmail("newuser@example.com");
     * userRepository.save(user);
     * 
     * // This method is called:
     * getCurrentAuditor() → Optional.empty()
     * 
     * // Database:
     * // created_by: null (acceptable for registration)
     * // created_at: 2025-12-20T17:30:00
     * }
     * </pre>
     * 
     * Scenario 3: System Background Job
     * <pre>
     * {@code
     * // Scheduled task runs (no user context)
     * @Scheduled(cron = "0 0 * * * *")
     * public void cleanupInactiveUsers() {
     *     user.setIsActive(false);
     *     userRepository.save(user);
     * }
     * 
     * // This method is called:
     * getCurrentAuditor() → Optional.empty()
     * 
     * // Database:
     * // updated_by: null (system operation)
     * // updated_at: 2025-12-20T18:00:00
     * }
     * </pre>
     * 
     * Alternative Implementations:
     * 
     * 1. Return "SYSTEM" for unauthenticated:
     * <pre>
     * {@code
     * return Optional.ofNullable(authentication)
     *     .map(auth -> auth.getName())
     *     .or(() -> Optional.of("SYSTEM"));
     * }
     * </pre>
     * 
     * 2. Extract from custom user details:
     * <pre>
     * {@code
     * return Optional.ofNullable(authentication)
     *     .filter(auth -> auth.getPrincipal() instanceof UserDetails)
     *     .map(auth -> ((UserDetails) auth.getPrincipal()).getUsername());
     * }
     * </pre>
     * 
     * 3. Use user ID instead of username:
     * <pre>
     * {@code
     * return Optional.ofNullable(authentication)
     *     .filter(auth -> auth.getPrincipal() instanceof CustomUserDetails)
     *     .map(auth -> ((CustomUserDetails) auth.getPrincipal()).getUserId().toString());
     * }
     * </pre>
     * 
     * Thread Safety:
     * - SecurityContextHolder uses ThreadLocal storage
     * - Each thread (HTTP request) has its own SecurityContext
     * - Safe for concurrent requests
     * - No synchronization needed
     * 
     * Performance:
     * - SecurityContextHolder.getContext() is O(1) - ThreadLocal lookup
     * - No database queries
     * - No network calls
     * - Very fast (< 1 microsecond)
     * 
     * @return Optional containing current user's username, or empty if not authenticated
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            // Get the current authentication from Spring Security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            // Check if user is authenticated
            if (authentication == null || !authentication.isAuthenticated()) {
                // No authenticated user (e.g., public endpoint, system job)
                return Optional.empty();
            }
            
            // Check if it's an anonymous user (Spring Security creates anonymous auth for public endpoints)
            if ("anonymousUser".equals(authentication.getPrincipal())) {
                return Optional.empty();
            }
            
            // Extract username from authentication principal
            // For simple cases, principal is the username string
            // For UserDetails, it's a UserDetails object with getUsername()
            String username;
            Object principal = authentication.getPrincipal();
            
            if (principal instanceof String) {
                // Simple case: principal is username
                username = (String) principal;
            } else {
                // Complex case: principal is UserDetails or custom object
                // Use getName() which works for all Authentication implementations
                username = authentication.getName();
            }
            
            return Optional.of(username);
        };
    }
}

