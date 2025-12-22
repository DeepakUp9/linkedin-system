package com.linkedin.user.dto.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.linkedin.user.dto.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for successful login response.
 * 
 * Purpose:
 * Returns authentication tokens and user information after successful login.
 * Used by POST /api/auth/login and POST /api/auth/register endpoints.
 * 
 * Components:
 * 1. Access Token: Short-lived JWT (15 minutes) for API authentication
 * 2. Refresh Token: Long-lived UUID (7 days) for getting new access tokens
 * 3. Token Type: Always "Bearer" (HTTP Authorization header format)
 * 4. Expires In: Access token lifetime in seconds (900 = 15 minutes)
 * 5. User: Basic user information (id, email, name, roles, etc.)
 * 
 * Token Usage by Client:
 * 1. Store both tokens securely (e.g., localStorage or secure cookie)
 * 2. Use access token for API calls:
 *    Authorization: Bearer <accessToken>
 * 3. When access token expires (after 15 min):
 *    - Call POST /api/auth/refresh with refreshToken
 *    - Get new accessToken
 * 4. When refresh token expires (after 7 days):
 *    - User must login again
 * 
 * Security Notes:
 * - Access token is a JWT (contains user info, signed by server)
 * - Refresh token is a UUID (stored in database, can be revoked)
 * - Both should be sent over HTTPS only
 * - Never store tokens in URL or query parameters
 * - Use HttpOnly cookies or secure localStorage
 * 
 * Example Response:
 * {
 *   "accessToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJqb2huQGV4YW1wbGUuY29tIiwicm9sZXMiOlsiVVNFUiJdLCJpYXQiOjE2MzQwNDAwMDAsImV4cCI6MTYzNDA0MDkwMH0.abc123...",
 *   "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
 *   "tokenType": "Bearer",
 *   "expiresIn": 900,
 *   "issuedAt": "2025-12-20T20:30:00",
 *   "user": {
 *     "id": 1,
 *     "email": "john@example.com",
 *     "name": "John Doe",
 *     "accountType": "BASIC",
 *     "roles": ["USER"],
 *     "isActive": true,
 *     "emailVerified": true
 *   }
 * }
 * 
 * Client Implementation Example (JavaScript):
 * ```javascript
 * // Login
 * const response = await fetch('/api/auth/login', {
 *   method: 'POST',
 *   headers: { 'Content-Type': 'application/json' },
 *   body: JSON.stringify({ email, password })
 * });
 * const { accessToken, refreshToken } = await response.json();
 * 
 * // Store tokens
 * localStorage.setItem('accessToken', accessToken);
 * localStorage.setItem('refreshToken', refreshToken);
 * 
 * // Use access token for API calls
 * const apiResponse = await fetch('/api/users/1', {
 *   headers: { 'Authorization': `Bearer ${accessToken}` }
 * });
 * ```
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    /**
     * JWT access token for API authentication.
     * 
     * Format: JWT with three parts (header.payload.signature)
     * Example: "eyJhbGciOiJIUzUxMiJ9.eyJzdWIi...abc123"
     * 
     * Lifetime: 15 minutes (900 seconds)
     * 
     * Usage:
     * - Add to Authorization header: "Bearer <token>"
     * - Server validates signature and expiration
     * - Server extracts user info from token
     * 
     * Contains (JWT payload):
     * - sub: User email
     * - userId: User ID
     * - roles: User roles (["USER"] or ["USER", "ADMIN"])
     * - iat: Issued at timestamp
     * - exp: Expiration timestamp
     * 
     * Security:
     * - Signed with server secret key (HS512)
     * - Cannot be modified without invalidating signature
     * - Stateless (server doesn't store it)
     * - Short-lived to limit damage if stolen
     */
    private String accessToken;
    
    /**
     * UUID refresh token for obtaining new access tokens.
     * 
     * Format: UUID string
     * Example: "550e8400-e29b-41d4-a716-446655440000"
     * 
     * Lifetime: 7 days (604800 seconds)
     * 
     * Usage:
     * - When access token expires, send this to /api/auth/refresh
     * - Server validates token exists in database and not expired
     * - Server returns new access token
     * 
     * Storage:
     * - Stored in database (can be revoked immediately)
     * - One user can have multiple refresh tokens (multiple devices)
     * - Deleted on logout
     * 
     * Security:
     * - Long-lived but can be revoked (logout functionality)
     * - Stored in database, not in JWT
     * - Each device gets unique refresh token
     * - Auto-rotated when close to expiry
     */
    private String refreshToken;
    
    /**
     * Token type (always "Bearer").
     * 
     * Used for HTTP Authorization header format:
     * Authorization: Bearer <accessToken>
     * 
     * This follows OAuth 2.0 standard.
     * 
     * "Bearer" means: "The bearer (holder) of this token is authorized"
     */
    private String tokenType;
    
    /**
     * Access token lifetime in seconds.
     * 
     * Value: 900 (15 minutes)
     * 
     * Purpose:
     * - Client knows when to refresh token
     * - Client can show "Session expires in X minutes"
     * - Client can proactively refresh before expiry
     * 
     * Calculation:
     * expiresIn = 900 seconds = 15 minutes
     * 
     * Client should refresh token at:
     * - Expiry time (900 seconds)
     * - Or proactively (e.g., after 13 minutes)
     */
    private Integer expiresIn;
    
    /**
     * Timestamp when tokens were issued.
     * 
     * Purpose:
     * - Client can calculate exact expiry time:
     *   expiryTime = issuedAt + expiresIn seconds
     * - Useful for debugging token issues
     * - Audit trail
     * 
     * Example:
     * issuedAt = "2025-12-20T20:30:00"
     * expiresIn = 900
     * expiryTime = "2025-12-20T20:45:00"
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime issuedAt;
    
    /**
     * User information (safe fields only).
     * 
     * Contains:
     * - id: User ID
     * - email: User email
     * - name: User name
     * - accountType: BASIC or PREMIUM
     * - roles: ["USER"] or ["USER", "ADMIN"]
     * - isActive: Account status
     * - emailVerified: Email verification status
     * 
     * Does NOT contain:
     * - password (security)
     * - audit fields (internal details)
     * 
     * Purpose:
     * - Display user info in UI
     * - Check user roles for client-side UI logic
     * - Personalize user experience
     * 
     * Note: Client should NOT rely on this for authorization.
     * Server always validates roles from JWT, not client data.
     */
    private UserResponse user;
    
    /**
     * Helper method to check if access token is about to expire.
     * Useful for proactive token refresh.
     * 
     * @param secondsBeforeExpiry How many seconds before expiry to consider "soon"
     * @return true if token expires within the specified seconds
     */
    public boolean isExpiringWithin(int secondsBeforeExpiry) {
        if (issuedAt == null || expiresIn == null) {
            return false;
        }
        LocalDateTime expiryTime = issuedAt.plusSeconds(expiresIn);
        LocalDateTime thresholdTime = LocalDateTime.now().plusSeconds(secondsBeforeExpiry);
        return expiryTime.isBefore(thresholdTime);
    }
    
    /**
     * Get the exact expiry timestamp for the access token.
     * 
     * @return Expiry timestamp
     */
    public LocalDateTime getExpiryTime() {
        if (issuedAt == null || expiresIn == null) {
            return null;
        }
        return issuedAt.plusSeconds(expiresIn);
    }
}

