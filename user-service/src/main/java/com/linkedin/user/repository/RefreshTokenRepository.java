package com.linkedin.user.repository;

import com.linkedin.user.model.RefreshToken;
import com.linkedin.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for RefreshToken entity.
 * Provides database access methods for refresh token management.
 * 
 * Purpose:
 * - Store and retrieve refresh tokens
 * - Validate token existence and expiration
 * - Delete expired or revoked tokens
 * - Support logout functionality (token revocation)
 * 
 * Usage:
 * - AuthenticationService: Create/validate/delete tokens
 * - RefreshTokenService: Core token management logic
 * - Scheduled cleanup job: Delete expired tokens
 * 
 * Design Pattern: Repository Pattern (Spring Data JPA)
 * - Abstract database operations
 * - Auto-implementation by Spring
 * - Custom queries for complex operations
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    
    // ==================== Basic Queries ====================
    
    /**
     * Find a refresh token by its token value.
     * Most common query - used when validating refresh tokens.
     * 
     * Flow:
     * 1. Client sends refresh token: POST /api/auth/refresh {"refreshToken": "abc123"}
     * 2. Server looks up token: findByToken("abc123")
     * 3. If found and not expired, issue new access token
     * 4. If not found or expired, return 401 Unauthorized
     * 
     * @param token The token value (UUID string)
     * @return Optional containing RefreshToken if found
     */
    Optional<RefreshToken> findByToken(String token);
    
    /**
     * Find all refresh tokens for a specific user.
     * Useful for:
     * - Viewing user's active sessions (devices)
     * - Logging out from all devices
     * - Debugging token issues
     * 
     * Example:
     * User has tokens for:
     * - iPhone (created 2 days ago)
     * - Laptop (created 1 hour ago)
     * - iPad (created 5 days ago)
     * 
     * @param user The user entity
     * @return List of all refresh tokens for this user
     */
    List<RefreshToken> findByUser(User user);
    
    /**
     * Find all refresh tokens for a user by user ID.
     * Alternative to findByUser when you only have the ID.
     * 
     * @param userId The user's ID
     * @return List of all refresh tokens for this user
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId")
    List<RefreshToken> findByUserId(@Param("userId") Long userId);
    
    // ==================== Token Validation ====================
    
    /**
     * Check if a refresh token exists.
     * Faster than findByToken if you only need to check existence.
     * 
     * @param token The token value
     * @return true if token exists
     */
    boolean existsByToken(String token);
    
    /**
     * Find all expired refresh tokens.
     * Used by scheduled cleanup job to delete old tokens.
     * 
     * Cleanup Strategy:
     * - Run daily: @Scheduled(cron = "0 0 2 * * *")  // 2 AM
     * - Find expired tokens: findByExpiryDateBefore(now)
     * - Delete them: deleteAll(expiredTokens)
     * 
     * Why cleanup?
     * - Prevent table bloat
     * - Improve query performance
     * - Reduce storage costs
     * 
     * @param now Current date/time
     * @return List of expired tokens
     */
    List<RefreshToken> findByExpiryDateBefore(LocalDateTime now);
    
    // ==================== Token Deletion ====================
    
    /**
     * Delete a refresh token by its token value.
     * Used for logout functionality.
     * 
     * Logout Flow:
     * 1. Client sends: POST /api/auth/logout {"refreshToken": "abc123"}
     * 2. Server deletes token: deleteByToken("abc123")
     * 3. Client can no longer use this token to get new access tokens
     * 4. User must login again
     * 
     * @param token The token value to delete
     * @return Number of tokens deleted (0 or 1)
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.token = :token")
    int deleteByToken(@Param("token") String token);
    
    /**
     * Delete all refresh tokens for a specific user.
     * Used for "logout from all devices" functionality.
     * 
     * Use Cases:
     * - User clicks "Logout from all devices"
     * - Account compromised (revoke all sessions)
     * - Password changed (force re-login everywhere)
     * 
     * @param user The user entity
     * @return Number of tokens deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user = :user")
    int deleteByUser(@Param("user") User user);
    
    /**
     * Delete all refresh tokens for a user by user ID.
     * Alternative to deleteByUser when you only have the ID.
     * 
     * @param userId The user's ID
     * @return Number of tokens deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
    
    /**
     * Delete all expired refresh tokens.
     * Used by scheduled cleanup job.
     * 
     * Performance Note:
     * - This can be a heavy operation if many tokens expired
     * - Run during off-peak hours (e.g., 2 AM)
     * - Consider batching if you have millions of users
     * 
     * @param now Current date/time
     * @return Number of tokens deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :now")
    int deleteByExpiryDateBefore(@Param("now") LocalDateTime now);
    
    // ==================== Statistics ====================
    
    /**
     * Count total refresh tokens for a user.
     * Shows how many active sessions the user has.
     * 
     * Typical Numbers:
     * - 0-2 sessions: Normal user (phone + laptop)
     * - 3-5 sessions: Power user (multiple devices)
     * - 10+ sessions: Suspicious (possible token theft)
     * 
     * @param user The user entity
     * @return Number of active tokens
     */
    long countByUser(User user);
    
    /**
     * Count all expired tokens in the system.
     * Useful for monitoring cleanup job effectiveness.
     * 
     * Health Check:
     * - If this number is high, cleanup job may not be running
     * - If this number is always 0, cleanup job is working well
     * 
     * @param now Current date/time
     * @return Number of expired tokens
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.expiryDate < :now")
    long countExpiredTokens(@Param("now") LocalDateTime now);
    
    /**
     * Get the oldest refresh token for a user.
     * Useful for implementing "maximum devices" limit.
     * 
     * Strategy:
     * 1. User tries to login (already has 5 tokens)
     * 2. Check: if countByUser(user) >= MAX_TOKENS
     * 3. Delete oldest token: findOldestByUser(user)
     * 4. Create new token
     * 
     * This ensures users don't accumulate unlimited tokens.
     * 
     * @param userId The user's ID
     * @return The oldest token for this user
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId ORDER BY rt.createdAt ASC LIMIT 1")
    Optional<RefreshToken> findOldestByUser(@Param("userId") Long userId);
}

