package com.linkedin.user.dto.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for refresh token response.
 * 
 * Purpose:
 * Returns a new access token (and optionally a new refresh token) after
 * successfully validating a refresh token.
 * Used by POST /api/auth/refresh endpoint.
 * 
 * Response Scenarios:
 * 
 * 1. Normal Refresh (refresh token still valid for > 1 day):
 *    - Return new access token only
 *    - Client keeps using the same refresh token
 * 
 * 2. Refresh with Token Rotation (refresh token expires in < 1 day):
 *    - Return new access token AND new refresh token
 *    - Delete old refresh token from database
 *    - Client should store new refresh token
 * 
 * Token Rotation Benefits:
 * - Extends user session without requiring login
 * - More secure: Even if refresh token is stolen, it expires soon
 * - Better UX: User stays logged in if they're active
 * 
 * Example Response (Normal Refresh):
 * {
 *   "accessToken": "eyJhbGci...",
 *   "refreshToken": null,  // Same refresh token can be used
 *   "tokenType": "Bearer",
 *   "expiresIn": 900,
 *   "issuedAt": "2025-12-20T20:45:00"
 * }
 * 
 * Example Response (With Token Rotation):
 * {
 *   "accessToken": "eyJhbGci...",
 *   "refreshToken": "9b2d5f8a-3c4e-41f5-b826-556677889900",  // NEW refresh token
 *   "tokenType": "Bearer",
 *   "expiresIn": 900,
 *   "issuedAt": "2025-12-20T20:45:00"
 * }
 * 
 * Client Implementation Example (JavaScript):
 * ```javascript
 * async function refreshAccessToken() {
 *   const oldRefreshToken = localStorage.getItem('refreshToken');
 *   
 *   const response = await fetch('/api/auth/refresh', {
 *     method: 'POST',
 *     headers: { 'Content-Type': 'application/json' },
 *     body: JSON.stringify({ refreshToken: oldRefreshToken })
 *   });
 *   
 *   if (response.ok) {
 *     const data = await response.json();
 *     
 *     // Always update access token
 *     localStorage.setItem('accessToken', data.accessToken);
 *     
 *     // If server sent new refresh token, update it
 *     if (data.refreshToken) {
 *       localStorage.setItem('refreshToken', data.refreshToken);
 *       console.log('Refresh token rotated');
 *     }
 *     
 *     return data.accessToken;
 *   } else {
 *     // Refresh token expired or invalid → Redirect to login
 *     localStorage.removeItem('accessToken');
 *     localStorage.removeItem('refreshToken');
 *     window.location.href = '/login';
 *   }
 * }
 * ```
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenResponse {
    
    /**
     * New JWT access token for API authentication.
     * 
     * Format: JWT with three parts (header.payload.signature)
     * Example: "eyJhbGciOiJIUzUxMiJ9.eyJzdWIi...abc123"
     * 
     * Lifetime: 15 minutes (900 seconds)
     * 
     * Usage:
     * - Replace old access token with this new one
     * - Add to Authorization header: "Bearer <token>"
     * 
     * This is always present in the response.
     */
    private String accessToken;
    
    /**
     * New refresh token (optional, only if token rotation occurred).
     * 
     * Format: UUID string
     * Example: "550e8400-e29b-41d4-a716-446655440000"
     * 
     * When Present:
     * - Old refresh token had < 1 day left
     * - Server issued new refresh token (7 days lifetime)
     * - Old refresh token was deleted from database
     * - Client MUST store this new refresh token
     * 
     * When Null:
     * - Old refresh token still has > 1 day left
     * - Client should keep using the same refresh token
     * 
     * Token Rotation Strategy:
     * - Refresh token expires in 7 days
     * - If user is active and refreshes token when < 1 day left
     * - Server issues new 7-day refresh token
     * - This extends the session for active users
     * - Inactive users (7+ days) must login again
     */
    private String refreshToken;
    
    /**
     * Token type (always "Bearer").
     * 
     * Used for HTTP Authorization header format:
     * Authorization: Bearer <accessToken>
     */
    private String tokenType;
    
    /**
     * Access token lifetime in seconds.
     * 
     * Value: 900 (15 minutes)
     * 
     * Purpose:
     * - Client knows when to refresh token again
     * - Calculate next refresh time: issuedAt + expiresIn
     */
    private Integer expiresIn;
    
    /**
     * Timestamp when the new access token was issued.
     * 
     * Purpose:
     * - Calculate exact expiry time: issuedAt + expiresIn seconds
     * - Useful for debugging
     * - Audit trail
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime issuedAt;
    
    /**
     * Check if this response includes a new refresh token (token rotation).
     * 
     * @return true if new refresh token was issued
     */
    public boolean hasNewRefreshToken() {
        return refreshToken != null && !refreshToken.isBlank();
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

