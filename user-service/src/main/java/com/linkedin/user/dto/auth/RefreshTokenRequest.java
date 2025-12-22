package com.linkedin.user.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for refresh token request.
 * 
 * Purpose:
 * Used to obtain a new access token when the current one expires.
 * Used by POST /api/auth/refresh endpoint.
 * 
 * Token Refresh Flow:
 * 1. User logs in → Gets access token (15 min) + refresh token (7 days)
 * 2. After 15 minutes, access token expires
 * 3. Client sends refresh token to /api/auth/refresh
 * 4. Server validates refresh token:
 *    - Checks if token exists in database
 *    - Checks if token is not expired
 *    - Checks if user account is still active
 * 5. If valid, server issues new access token
 * 6. Client uses new access token for API calls
 * 
 * Why Refresh Tokens?
 * - Access tokens are short-lived (15 min) for security
 * - Short-lived tokens limit damage if stolen
 * - But users don't want to login every 15 minutes
 * - Refresh tokens allow long sessions (7 days) without compromising security
 * - If refresh token is stolen, it can be revoked (logout)
 * 
 * Security Notes:
 * - Refresh tokens are stored in database (can be revoked)
 * - Access tokens are JWTs (stateless, cannot be revoked until expiry)
 * - Refresh tokens should be stored securely (HttpOnly cookie or secure storage)
 * - Never send refresh token in URL or query parameters
 * - Always use HTTPS
 * 
 * Token Rotation:
 * When a refresh token is close to expiry (< 1 day left), server may:
 * - Issue a new refresh token along with the access token
 * - Delete the old refresh token
 * - This extends the user's session without requiring login
 * 
 * Example Request:
 * POST /api/auth/refresh
 * {
 *   "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
 * }
 * 
 * Example Response (Success):
 * {
 *   "accessToken": "eyJhbGci...",
 *   "tokenType": "Bearer",
 *   "expiresIn": 900
 * }
 * 
 * Example Response (Expired Token):
 * {
 *   "errorCode": "REFRESH_TOKEN_EXPIRED",
 *   "message": "Refresh token has expired. Please login again.",
 *   "status": 401
 * }
 * 
 * Example Response (Invalid Token):
 * {
 *   "errorCode": "INVALID_REFRESH_TOKEN",
 *   "message": "Invalid or revoked refresh token",
 *   "status": 401
 * }
 * 
 * Client Implementation Example (JavaScript):
 * ```javascript
 * async function refreshAccessToken() {
 *   const refreshToken = localStorage.getItem('refreshToken');
 *   
 *   const response = await fetch('/api/auth/refresh', {
 *     method: 'POST',
 *     headers: { 'Content-Type': 'application/json' },
 *     body: JSON.stringify({ refreshToken })
 *   });
 *   
 *   if (response.ok) {
 *     const { accessToken } = await response.json();
 *     localStorage.setItem('accessToken', accessToken);
 *     return accessToken;
 *   } else {
 *     // Refresh token expired or invalid
 *     // Redirect to login page
 *     window.location.href = '/login';
 *   }
 * }
 * 
 * // Proactive refresh before access token expires
 * setInterval(() => {
 *   refreshAccessToken();
 * }, 14 * 60 * 1000); // Every 14 minutes (before 15 min expiry)
 * ```
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {
    
    /**
     * The refresh token value (UUID).
     * 
     * Format: UUID string
     * Example: "550e8400-e29b-41d4-a716-446655440000"
     * 
     * Validation:
     * - @NotBlank: Cannot be null, empty, or whitespace
     * 
     * Lookup Process:
     * 1. Server looks up token in database: SELECT * FROM refresh_tokens WHERE token = ?
     * 2. If not found → 401 Unauthorized (invalid token)
     * 3. If found but expired → 401 Unauthorized (expired token)
     * 4. If found and valid → Issue new access token
     * 
     * Security:
     * - This is NOT a JWT. It's a simple UUID.
     * - Stored in database (can be revoked immediately)
     * - Each refresh invalidates the old one (token rotation)
     * - Multiple devices have different refresh tokens
     * 
     * Storage Location:
     * - Best: HttpOnly secure cookie (cannot be accessed by JavaScript)
     * - Alternative: Secure localStorage (vulnerable to XSS)
     * - Never: URL or query parameters (logged in server logs)
     */
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}

