package com.linkedin.connection.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Configuration class for Spring Data JPA Auditing.
 * 
 * Purpose:
 * Enables automatic auditing of entities with @CreatedDate, @LastModifiedDate,
 * @CreatedBy, and @LastModifiedBy annotations.
 * 
 * What It Does:
 * 1. Activates JPA Auditing via @EnableJpaAuditing
 * 2. Provides an AuditorAware bean to capture the current user
 * 3. Automatically populates audit fields when entities are saved
 * 
 * How It Works:
 * <pre>
 * Entity Creation:
 *   Connection conn = new Connection();
 *   connectionRepository.save(conn);
 *   
 *   → JPA Auditing automatically sets:
 *     - createdAt = current timestamp
 *     - updatedAt = current timestamp
 *     - createdBy = current user (if @CreatedBy is used)
 *     - updatedBy = current user (if @LastModifiedBy is used)
 * 
 * Entity Update:
 *   conn.setState(ACCEPTED);
 *   connectionRepository.save(conn);
 *   
 *   → JPA Auditing automatically updates:
 *     - updatedAt = current timestamp
 *     - updatedBy = current user (if @LastModifiedBy is used)
 * </pre>
 * 
 * Benefits:
 * - Automatic timestamp management (no manual LocalDateTime.now())
 * - Consistent auditing across all entities
 * - Tracks who made changes (useful for compliance)
 * - Reduces boilerplate code
 * 
 * Example Entity:
 * <pre>
 * {@code
 * @Entity
 * @EntityListeners(AuditingEntityListener.class)
 * public class Connection {
 *     @CreatedDate
 *     private LocalDateTime createdAt;
 *     
 *     @LastModifiedDate
 *     private LocalDateTime updatedAt;
 *     
 *     @CreatedBy
 *     private Long createdBy;
 *     
 *     @LastModifiedBy
 *     private Long lastModifiedBy;
 * }
 * }
 * </pre>
 * 
 * Current Implementation:
 * - Uses timestamps only (createdAt, updatedAt)
 * - Does NOT track user IDs (createdBy, lastModifiedBy) yet
 * - Can be enhanced to capture current user from SecurityContext
 * 
 * Future Enhancement:
 * To track WHO made changes:
 * <pre>
 * {@code
 * @Bean
 * public AuditorAware<Long> auditorProvider() {
 *     return () -> {
 *         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *         if (auth == null || !auth.isAuthenticated()) {
 *             return Optional.empty();
 *         }
 *         // Extract user ID from JWT token
 *         Long userId = extractUserIdFromAuth(auth);
 *         return Optional.ofNullable(userId);
 *     };
 * }
 * }
 * </pre>
 * 
 * Related:
 * @see org.springframework.data.jpa.domain.support.AuditingEntityListener
 * @see org.springframework.data.annotation.CreatedDate
 * @see org.springframework.data.annotation.LastModifiedDate
 * @see com.linkedin.connection.model.Connection
 * 
 * @author LinkedIn System
 * @version 1.0
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditingConfig {

    /**
     * Provides the current auditor (user) for JPA Auditing.
     * 
     * Current Implementation:
     * Returns empty Optional, meaning @CreatedBy and @LastModifiedBy will not be populated.
     * This is intentional as we're currently only using timestamp auditing.
     * 
     * When to Enable User Tracking:
     * - Add @CreatedBy and @LastModifiedBy fields to entities
     * - Implement logic to extract user ID from SecurityContext
     * - Return Optional.of(userId) instead of Optional.empty()
     * 
     * Security Considerations:
     * - Ensure authentication is always present when modifying entities
     * - Use a system user ID for scheduled jobs/migrations
     * - Log audit trail changes for compliance
     * 
     * @return AuditorAware implementation returning current user ID (or empty if not tracked)
     */
    @Bean
    public AuditorAware<Long> auditorProvider() {
        // TODO: Implement user tracking when needed
        // For now, we only track timestamps, not user IDs
        return () -> {
            // Option 1: Return empty (current approach)
            return Optional.empty();
            
            // Option 2: Get user from SecurityContext (for future implementation)
            /*
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated() 
                || authentication instanceof AnonymousAuthenticationToken) {
                return Optional.empty();
            }
            
            // Extract user ID from JWT claims or UserDetails
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                // Custom UserDetails with userId
                CustomUserDetails userDetails = (CustomUserDetails) principal;
                return Optional.of(userDetails.getUserId());
            }
            
            // Fallback: Try to get from JWT claims
            if (authentication instanceof JwtAuthenticationToken) {
                JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
                Long userId = jwtAuth.getToken().getClaim("userId");
                return Optional.ofNullable(userId);
            }
            
            return Optional.empty();
            */
        };
    }

    /**
     * Alternative: System user ID for background jobs.
     * Can be used for scheduled tasks, migrations, or system operations.
     * 
     * Usage:
     * <pre>
     * {@code
     * @Scheduled(fixedDelay = 3600000)
     * public void cleanupOldConnections() {
     *     // Use system user for audit trail
     *     SecurityContextHolder.getContext().setAuthentication(
     *         new UsernamePasswordAuthenticationToken("system", null, authorities)
     *     );
     *     
     *     // Perform cleanup
     *     connectionRepository.deleteOldConnections(...);
     * }
     * }
     * </pre>
     */
    public static final Long SYSTEM_USER_ID = 0L;
}

