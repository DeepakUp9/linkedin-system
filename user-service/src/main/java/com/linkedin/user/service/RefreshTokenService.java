package com.linkedin.user.service;

import com.linkedin.common.exceptions.ResourceNotFoundException;
import com.linkedin.common.exceptions.ValidationException;
import com.linkedin.user.model.RefreshToken;
import com.linkedin.user.model.User;
import com.linkedin.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing refresh tokens in the authentication system.
 * 
 * Purpose:
 * Refresh tokens enable long-lived sessions (7 days) without compromising security.
 * They are stored in the database and can be revoked immediately (logout).
 * 
 * Token Lifecycle:
 * 
 * 1. Creation (Login/Register):
 *    - Generate UUID token
 *    - Store in database with 7-day expiration
 *    - Return token to client
 * 
 * 2. Usage (Token Refresh):
 *    - Client sends refresh token
 *    - Server validates: exists? expired? user active?
 *    - If valid, generate new access token
 *    - If close to expiry, rotate (issue new refresh token)
 * 
 * 3. Revocation (Logout):
 *    - Delete refresh token from database
 *    - Token immediately invalid
 *    - Cannot be used to get new access tokens
 * 
 * 4. Expiration (Time-based):
 *    - After 7 days, token expires
 *    - Validation fails
 *    - User must login again
 * 
 * 5. Cleanup (Scheduled):
 *    - Daily job removes expired tokens
 *    - Prevents database bloat
 *    - Improves query performance
 * 
 * Why Refresh Tokens?
 * 
 * Access Token Only (Without Refresh Token):
 * ❌ Short expiration (15 min) → Frequent re-login → Bad UX
 * ❌ Long expiration (24 hours) → Token theft risk → Security issue
 * 
 * Access Token + Refresh Token (Our Approach):
 * ✅ Access token: Short-lived (15 min) → Secure
 * ✅ Refresh token: Long-lived (7 days) → Good UX
 * ✅ Refresh tokens stored in DB → Can be revoked (logout)
 * ✅ Token rotation → Extended sessions for active users
 * 
 * Security Features:
 * 
 * 1. Database Storage:
 *    - Not JWTs (cannot be verified without database)
 *    - Can be revoked immediately
 *    - Centralized control
 * 
 * 2. Token Rotation:
 *    - When token has < 1 day left, issue new one
 *    - Delete old token
 *    - Extends session for active users
 *    - Inactive users must re-login after 7 days
 * 
 * 3. User Validation:
 *    - Check if user account is still active
 *    - Check if email is verified (optional)
 *    - Prevent disabled accounts from refreshing
 * 
 * 4. Device Management:
 *    - One user can have multiple refresh tokens (multiple devices)
 *    - Each device has unique token
 *    - Logout from one device doesn't affect others
 *    - "Logout from all devices" option available
 * 
 * 5. Automatic Cleanup:
 *    - Scheduled job runs daily at 2 AM
 *    - Deletes expired tokens
 *    - Maintains database health
 * 
 * Configuration (application.yml):
 * <pre>
 * {@code
 * app:
 *   refresh-token:
 *     expiration: 604800000  # 7 days in milliseconds
 * }
 * </pre>
 * 
 * Usage Example:
 * <pre>
 * {@code
 * // Login: Create refresh token
 * RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
 * return LoginResponse.builder()
 *     .accessToken(accessToken)
 *     .refreshToken(refreshToken.getToken())
 *     .build();
 * 
 * // Refresh: Validate and maybe rotate
 * RefreshToken validToken = refreshTokenService.validateRefreshToken(tokenString);
 * String newAccessToken = jwtTokenProvider.generateTokenForUser(validToken.getUser());
 * 
 * if (validToken.needsRotation()) {
 *     RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(validToken);
 *     // Return both new access token and new refresh token
 * }
 * 
 * // Logout: Delete token
 * refreshTokenService.deleteByToken(tokenString);
 * }
 * </pre>
 * 
 * @see RefreshToken
 * @see RefreshTokenRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Refresh token expiration time in milliseconds.
     * Injected from application.yml: app.refresh-token.expiration
     * 
     * Default: 604800000 ms = 7 days
     * 
     * Common values:
     * - 604800000 (7 days) - Balanced security and UX
     * - 1209600000 (14 days) - More user convenience
     * - 2592000000 (30 days) - Maximum convenience
     * 
     * Trade-offs:
     * - Longer expiration: Better UX, but higher risk if token stolen
     * - Shorter expiration: More secure, but more frequent logins
     * 
     * Recommendation: 7 days with token rotation
     */
    @Value("${app.refresh-token.expiration:604800000}")
    private long refreshTokenExpirationMs;

    // ==================== CREATE OPERATIONS ====================

    /**
     * Creates a new refresh token for a user.
     * 
     * This method is called after successful login or registration.
     * 
     * Process:
     * 1. Generate random UUID token
     * 2. Calculate expiration date (now + 7 days)
     * 3. Create RefreshToken entity
     * 4. Save to database
     * 5. Return token
     * 
     * Token Format:
     * - UUID: "550e8400-e29b-41d4-a716-446655440000"
     * - Not a JWT (simple UUID)
     * - Random, unguessable
     * - Stored in database
     * 
     * Multiple Devices:
     * - User can have multiple refresh tokens
     * - Each login creates a new token
     * - Each device keeps its own token
     * - Logout from one device doesn't affect others
     * 
     * Example:
     * <pre>
     * {@code
     * // After successful login
     * User user = userRepository.findByEmail("john@example.com").orElseThrow();
     * RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
     * 
     * // Return to client
     * return LoginResponse.builder()
     *     .accessToken(accessToken)
     *     .refreshToken(refreshToken.getToken())  // UUID string
     *     .build();
     * }
     * </pre>
     * 
     * Database Entry:
     * - id: Auto-generated
     * - token: "550e8400-e29b-41d4-a716-446655440000"
     * - user_id: 123
     * - expiry_date: 2025-12-27 20:00:00 (7 days from now)
     * - created_at: 2025-12-20 20:00:00
     * 
     * @param user User entity for whom to create the refresh token
     * @return Created refresh token entity with generated token value
     */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        log.info("Creating refresh token for user: {} (ID: {})", user.getEmail(), user.getId());

        // Generate unique UUID token
        String tokenValue = UUID.randomUUID().toString();

        // Calculate expiration date
        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000);

        // Build refresh token entity
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .user(user)
                .expiryDate(expiryDate)
                .build();

        // Save to database
        RefreshToken savedToken = refreshTokenRepository.save(refreshToken);

        log.info("Refresh token created successfully for user {}: token expires at {}", 
                 user.getEmail(), expiryDate);

        return savedToken;
    }

    // ==================== VALIDATION OPERATIONS ====================

    /**
     * Validates a refresh token string and returns the token entity.
     * 
     * This method performs comprehensive validation:
     * 1. Check if token exists in database
     * 2. Check if token is not expired
     * 3. Check if user account is still active
     * 
     * Validation Flow:
     * <pre>
     * {@code
     * Client sends refresh token
     *   ↓
     * Look up token in database
     *   ↓
     * Token found? → Yes
     *   ↓
     * Token expired? → No
     *   ↓
     * User active? → Yes
     *   ↓
     * Token is valid! Return RefreshToken entity
     * }
     * </pre>
     * 
     * Error Cases:
     * 
     * 1. Token Not Found:
     *    - Token doesn't exist in database
     *    - Means: Invalid token or already deleted (logout)
     *    - Action: Throw ResourceNotFoundException
     *    - HTTP: 404 Not Found
     * 
     * 2. Token Expired:
     *    - expiry_date < current time
     *    - Means: Token lifetime exceeded (> 7 days)
     *    - Action: Delete token, throw ValidationException
     *    - HTTP: 401 Unauthorized
     * 
     * 3. User Inactive:
     *    - user.isActive = false
     *    - Means: Account disabled or suspended
     *    - Action: Delete token, throw ValidationException
     *    - HTTP: 401 Unauthorized
     * 
     * Usage Example:
     * <pre>
     * {@code
     * // In refresh endpoint
     * try {
     *     RefreshToken refreshToken = refreshTokenService.validateRefreshToken(tokenString);
     *     User user = refreshToken.getUser();
     *     
     *     // Generate new access token
     *     String newAccessToken = jwtTokenProvider.generateTokenForUser(user);
     *     
     *     return RefreshTokenResponse.builder()
     *         .accessToken(newAccessToken)
     *         .build();
     * } catch (ResourceNotFoundException ex) {
     *     // Return 404: Invalid token
     * } catch (ValidationException ex) {
     *     // Return 401: Expired or user inactive
     * }
     * }
     * </pre>
     * 
     * Security Notes:
     * - Always validate before using token
     * - Check user status (active, verified)
     * - Delete invalid tokens to maintain database health
     * - Log validation failures for security monitoring
     * 
     * @param tokenValue The refresh token string (UUID)
     * @return Validated refresh token entity with user loaded
     * @throws ResourceNotFoundException if token doesn't exist
     * @throws ValidationException if token is expired or user is inactive
     */
    @Transactional
    public RefreshToken validateRefreshToken(String tokenValue) {
        log.debug("Validating refresh token: {}", tokenValue);

        // 1. Find token in database
        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found: {}", tokenValue);
                    return new ResourceNotFoundException("Refresh token not found", "REFRESH_TOKEN_NOT_FOUND");
                });

        log.debug("Refresh token found for user: {}", refreshToken.getUser().getEmail());

        // 2. Check if token is expired
        if (refreshToken.isExpired()) {
            log.warn("Refresh token has expired for user: {}", refreshToken.getUser().getEmail());
            
            // Delete expired token
            refreshTokenRepository.delete(refreshToken);
            log.debug("Expired refresh token deleted");
            
            throw new ValidationException(
                "Refresh token has expired. Please login again.",
                "REFRESH_TOKEN_EXPIRED"
            );
        }

        // 3. Check if user account is active
        User user = refreshToken.getUser();
        if (!user.isAccountActive()) {
            log.warn("User account is inactive for refresh token: {}", user.getEmail());
            
            // Delete token for inactive user
            refreshTokenRepository.delete(refreshToken);
            log.debug("Refresh token deleted for inactive user");
            
            throw new ValidationException(
                "User account is inactive. Cannot refresh token.",
                "USER_ACCOUNT_INACTIVE"
            );
        }

        log.debug("Refresh token validation successful for user: {}", user.getEmail());
        return refreshToken;
    }

    /**
     * Finds a refresh token by its value without validation.
     * 
     * This is useful when you need to check token existence
     * without throwing exceptions.
     * 
     * Use Cases:
     * - Check if token exists before deleting
     * - Conditional token operations
     * - Non-critical token lookups
     * 
     * For authentication purposes, always use validateRefreshToken().
     * 
     * @param tokenValue The refresh token string
     * @return Optional containing RefreshToken if found, empty otherwise
     */
    public Optional<RefreshToken> findByToken(String tokenValue) {
        log.debug("Finding refresh token: {}", tokenValue);
        return refreshTokenRepository.findByToken(tokenValue);
    }

    // ==================== TOKEN ROTATION ====================

    /**
     * Rotates a refresh token (issues new token, deletes old one).
     * 
     * Token rotation is a security best practice that:
     * - Issues a new refresh token with fresh 7-day expiration
     * - Deletes the old refresh token
     * - Extends session for active users
     * - Prevents token reuse attacks
     * 
     * When to Rotate:
     * - Old token has < 1 day remaining (see RefreshToken.needsRotation())
     * - User is actively using the application
     * - Provides seamless experience (no re-login needed)
     * 
     * Rotation Flow:
     * <pre>
     * {@code
     * Client sends old refresh token (6.5 days old, 0.5 days left)
     *   ↓
     * Server validates token
     *   ↓
     * Server checks: needsRotation()? → Yes (< 1 day left)
     *   ↓
     * Server creates new refresh token (7 days expiration)
     *   ↓
     * Server deletes old refresh token
     *   ↓
     * Server returns: New access token + New refresh token
     *   ↓
     * Client stores new refresh token
     *   ↓
     * Session extended by 7 more days!
     * }
     * </pre>
     * 
     * Benefits:
     * - Active users never experience session expiration
     * - Inactive users (> 7 days) must re-login
     * - Old tokens cannot be reused (deleted immediately)
     * - Fresh expiration reduces security risk
     * 
     * Example Usage:
     * <pre>
     * {@code
     * // In refresh endpoint
     * RefreshToken oldToken = refreshTokenService.validateRefreshToken(tokenString);
     * String newAccessToken = jwtTokenProvider.generateTokenForUser(oldToken.getUser());
     * 
     * RefreshTokenResponse.RefreshTokenResponseBuilder responseBuilder = 
     *     RefreshTokenResponse.builder()
     *         .accessToken(newAccessToken)
     *         .tokenType("Bearer")
     *         .expiresIn(jwtTokenProvider.getExpirationInSeconds());
     * 
     * // Check if rotation needed
     * if (oldToken.needsRotation()) {
     *     log.info("Rotating refresh token for user: {}", oldToken.getUser().getEmail());
     *     RefreshToken newToken = refreshTokenService.rotateRefreshToken(oldToken);
     *     responseBuilder.refreshToken(newToken.getToken());  // Include new refresh token
     * }
     * 
     * return responseBuilder.build();
     * }
     * </pre>
     * 
     * Security Notes:
     * - Old token is deleted immediately (cannot be reused)
     * - New token has full 7-day expiration
     * - Client MUST update stored refresh token
     * - If client uses old token again, it will fail (already deleted)
     * 
     * @param oldToken The existing refresh token to rotate
     * @return New refresh token with fresh expiration
     */
    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldToken) {
        log.info("Rotating refresh token for user: {} (ID: {})", 
                 oldToken.getUser().getEmail(), oldToken.getUser().getId());

        // Create new refresh token
        RefreshToken newToken = createRefreshToken(oldToken.getUser());

        // Delete old refresh token
        refreshTokenRepository.delete(oldToken);
        log.debug("Old refresh token deleted during rotation");

        log.info("Refresh token rotated successfully for user: {}", oldToken.getUser().getEmail());
        return newToken;
    }

    // ==================== DELETE OPERATIONS ====================

    /**
     * Deletes a refresh token by its value (logout from single device).
     * 
     * This method implements logout functionality for a single device.
     * 
     * Logout Flow:
     * 1. Client sends: POST /api/auth/logout {refreshToken: "..."}
     * 2. Server deletes token from database
     * 3. Token immediately invalid
     * 4. Client cannot refresh access token anymore
     * 5. User must login again on this device
     * 
     * Why Delete Token?
     * - Refresh tokens are long-lived (7 days)
     * - Without deletion, stolen token could be used for 7 days
     * - Deletion provides immediate revocation
     * - Essential for security
     * 
     * Single Device Logout:
     * - Only deletes the specific refresh token sent
     * - User's other devices remain logged in
     * - Each device has unique refresh token
     * 
     * Example:
     * <pre>
     * {@code
     * // Logout endpoint
     * @PostMapping("/logout")
     * public ResponseEntity<ApiResponse<Void>> logout(
     *     @RequestBody LogoutRequest request
     * ) {
     *     refreshTokenService.deleteByToken(request.getRefreshToken());
     *     return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
     * }
     * }
     * </pre>
     * 
     * @param tokenValue The refresh token string to delete
     * @return Number of tokens deleted (0 if not found, 1 if deleted)
     */
    @Transactional
    public int deleteByToken(String tokenValue) {
        log.info("Deleting refresh token: {}", tokenValue);
        
        int deleted = refreshTokenRepository.deleteByToken(tokenValue);
        
        if (deleted > 0) {
            log.info("Refresh token deleted successfully");
        } else {
            log.warn("Refresh token not found for deletion: {}", tokenValue);
        }
        
        return deleted;
    }

    /**
     * Deletes all refresh tokens for a user (logout from all devices).
     * 
     * This method implements "logout from all devices" functionality.
     * 
     * Use Cases:
     * - User clicks "Logout from all devices" button
     * - Security response to potential compromise
     * - Password change (force re-login everywhere)
     * - Account suspension
     * 
     * Effect:
     * - All devices lose ability to refresh access tokens
     * - After current access tokens expire (15 min), all devices logged out
     * - User must login again on all devices
     * 
     * Example Scenario:
     * User has 3 devices:
     * - iPhone (refresh token A)
     * - Laptop (refresh token B)
     * - iPad (refresh token C)
     * 
     * User clicks "Logout from all devices":
     * - Tokens A, B, C all deleted
     * - All devices need re-login
     * 
     * Example:
     * <pre>
     * {@code
     * // Logout all devices endpoint
     * @PostMapping("/logout-all")
     * public ResponseEntity<ApiResponse<Void>> logoutAllDevices(
     *     @AuthenticationPrincipal User user
     * ) {
     *     int deleted = refreshTokenService.deleteAllByUser(user);
     *     return ResponseEntity.ok(
     *         ApiResponse.success(null, "Logged out from " + deleted + " devices")
     *     );
     * }
     * 
     * // After password change
     * public void changePassword(User user, String newPassword) {
     *     user.setPassword(passwordEncoder.encode(newPassword));
     *     userRepository.save(user);
     *     
     *     // Force re-login on all devices
     *     refreshTokenService.deleteAllByUser(user);
     * }
     * }
     * </pre>
     * 
     * @param user User entity whose tokens to delete
     * @return Number of tokens deleted
     */
    @Transactional
    public int deleteAllByUser(User user) {
        log.info("Deleting all refresh tokens for user: {} (ID: {})", user.getEmail(), user.getId());
        
        int deleted = refreshTokenRepository.deleteByUser(user);
        
        log.info("{} refresh token(s) deleted for user: {}", deleted, user.getEmail());
        return deleted;
    }

    /**
     * Deletes a specific refresh token entity.
     * 
     * This is a convenience method when you already have the RefreshToken entity.
     * 
     * @param refreshToken The refresh token entity to delete
     */
    @Transactional
    public void delete(RefreshToken refreshToken) {
        log.info("Deleting refresh token for user: {}", refreshToken.getUser().getEmail());
        refreshTokenRepository.delete(refreshToken);
        log.debug("Refresh token deleted");
    }

    // ==================== CLEANUP OPERATIONS ====================

    /**
     * Deletes all expired refresh tokens (scheduled cleanup job).
     * 
     * This method runs automatically every day at 2 AM to remove expired tokens.
     * 
     * Why Cleanup?
     * - Expired tokens are useless (cannot be validated)
     * - They consume database storage
     * - They slow down queries
     * - Regular cleanup maintains database health
     * 
     * Scheduling:
     * - Runs daily at 2 AM (off-peak hours)
     * - Cron: "0 0 2 * * *" (second minute hour day month weekday)
     * - Can be configured in application.yml
     * 
     * Process:
     * 1. Find all tokens where expiry_date < now
     * 2. Delete them in batch
     * 3. Log number of tokens deleted
     * 4. Monitor for unusual patterns
     * 
     * Expected Behavior:
     * - Fresh system: Few expired tokens
     * - Active system: Hundreds to thousands daily
     * - No tokens expired: Job may not be running
     * 
     * Performance:
     * - Index on expiry_date makes query fast
     * - Batch delete is efficient
     * - Runs during off-peak hours
     * 
     * Monitoring:
     * - Check logs daily
     * - Alert if no tokens deleted for several days
     * - Alert if very high number deleted (potential issue)
     * 
     * Manual Execution:
     * <pre>
     * {@code
     * // Can be called manually if needed
     * refreshTokenService.deleteExpiredTokens();
     * }
     * </pre>
     * 
     * @return Number of expired tokens deleted
     */
    @Transactional
    @Scheduled(cron = "0 0 2 * * *")  // Every day at 2 AM
    public int deleteExpiredTokens() {
        log.info("Starting scheduled cleanup of expired refresh tokens");
        
        LocalDateTime now = LocalDateTime.now();
        int deleted = refreshTokenRepository.deleteByExpiryDateBefore(now);
        
        if (deleted > 0) {
            log.info("Cleanup completed: {} expired refresh token(s) deleted", deleted);
        } else {
            log.info("Cleanup completed: No expired refresh tokens found");
        }
        
        return deleted;
    }

    // ==================== STATISTICS ====================

    /**
     * Counts total refresh tokens for a user (active sessions).
     * 
     * This shows how many devices/sessions the user has.
     * 
     * Typical Numbers:
     * - 0-2: Normal user (phone + laptop)
     * - 3-5: Power user (multiple devices)
     * - 10+: Suspicious (possible token theft or testing)
     * 
     * Use Cases:
     * - Display "Active Sessions" in user settings
     * - Security monitoring (unusual token count)
     * - Enforce maximum devices limit
     * 
     * Example UI:
     * "You are logged in on 3 devices. [View Sessions]"
     * 
     * @param user User entity
     * @return Number of active refresh tokens for this user
     */
    public long countUserTokens(User user) {
        long count = refreshTokenRepository.countByUser(user);
        log.debug("User {} has {} active refresh token(s)", user.getEmail(), count);
        return count;
    }

    /**
     * Counts all expired tokens in the system.
     * 
     * Health Check:
     * - High number: Cleanup job may not be running
     * - Always zero: Cleanup job is working well
     * 
     * @return Number of expired tokens
     */
    public long countExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        long count = refreshTokenRepository.countExpiredTokens(now);
        log.debug("System has {} expired refresh token(s)", count);
        return count;
    }

    /**
     * Gets all refresh tokens for a user.
     * 
     * Useful for:
     * - "Active Sessions" management UI
     * - Showing device list with creation times
     * - Allowing user to revoke specific sessions
     * 
     * Example Response:
     * - iPhone (created 2 days ago) [Revoke]
     * - Laptop (created 1 hour ago) [Revoke]
     * - iPad (created 5 days ago, expires in 2 days) [Revoke]
     * 
     * @param user User entity
     * @return List of all refresh tokens for this user
     */
    public List<RefreshToken> getUserTokens(User user) {
        log.debug("Getting all refresh tokens for user: {}", user.getEmail());
        return refreshTokenRepository.findByUser(user);
    }
}

