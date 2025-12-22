package com.linkedin.user.dto.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.linkedin.user.dto.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for successful registration response.
 * 
 * Purpose:
 * Returns authentication tokens and user information after successful registration.
 * Used by POST /api/auth/register endpoint.
 * 
 * This is essentially the same as LoginResponse because after successful registration,
 * the user is automatically logged in (gets access token + refresh token).
 * 
 * Design Note:
 * We could have used LoginResponse directly, but having a separate RegisterResponse:
 * 1. Makes API clearer (register returns RegisterResponse, login returns LoginResponse)
 * 2. Allows future divergence (e.g., registration might include email verification link)
 * 3. Better for API documentation (clear what each endpoint returns)
 * 
 * Auto-Login After Registration:
 * - User registers → Account created → Tokens generated → User logged in
 * - Better UX: User doesn't need to login after registration
 * - Client receives tokens immediately and can make authenticated requests
 * 
 * Email Verification Flow (Future):
 * 1. User registers → Account created with emailVerified = false
 * 2. Send verification email with link
 * 3. User is logged in but some features restricted until verified
 * 4. User clicks link → Email verified → Full access granted
 * 
 * Example Response:
 * {
 *   "accessToken": "eyJhbGci...",
 *   "refreshToken": "550e8400-...",
 *   "tokenType": "Bearer",
 *   "expiresIn": 900,
 *   "issuedAt": "2025-12-20T20:30:00",
 *   "user": {
 *     "id": 42,
 *     "email": "john@example.com",
 *     "name": "John Doe",
 *     "accountType": "BASIC",
 *     "roles": ["USER"],
 *     "isActive": true,
 *     "emailVerified": false,  // Not verified yet
 *     "createdAt": "2025-12-20T20:30:00"
 *   }
 * }
 * 
 * Client Implementation Example (JavaScript):
 * ```javascript
 * // Register
 * const response = await fetch('/api/auth/register', {
 *   method: 'POST',
 *   headers: { 'Content-Type': 'application/json' },
 *   body: JSON.stringify({
 *     email: 'john@example.com',
 *     password: 'SecurePass123',
 *     name: 'John Doe'
 *   })
 * });
 * 
 * if (response.ok) {
 *   const data = await response.json();
 *   
 *   // Store tokens (user is now logged in)
 *   localStorage.setItem('accessToken', data.accessToken);
 *   localStorage.setItem('refreshToken', data.refreshToken);
 *   
 *   // Show welcome message
 *   console.log(`Welcome, ${data.user.name}!`);
 *   
 *   // Check if email needs verification
 *   if (!data.user.emailVerified) {
 *     showEmailVerificationBanner();
 *   }
 *   
 *   // Redirect to dashboard
 *   window.location.href = '/dashboard';
 * } else {
 *   // Handle error (email exists, validation failed, etc.)
 *   const error = await response.json();
 *   showError(error.message);
 * }
 * ```
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    
    /**
     * JWT access token for API authentication.
     * 
     * Lifetime: 15 minutes (900 seconds)
     * 
     * Usage:
     * - Add to Authorization header: "Bearer <token>"
     * - Client can immediately make authenticated requests
     */
    private String accessToken;
    
    /**
     * UUID refresh token for obtaining new access tokens.
     * 
     * Lifetime: 7 days (604800 seconds)
     * 
     * Usage:
     * - When access token expires, send this to /api/auth/refresh
     * - Store securely (HttpOnly cookie or secure localStorage)
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
     */
    private Integer expiresIn;
    
    /**
     * Timestamp when tokens were issued.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime issuedAt;
    
    /**
     * Newly created user information.
     * 
     * Includes:
     * - id: New user's ID
     * - email: User's email
     * - name: User's name
     * - accountType: BASIC or PREMIUM
     * - roles: ["USER"] (default for new users)
     * - isActive: true
     * - emailVerified: false (typically not verified at registration)
     * - createdAt: Registration timestamp
     * 
     * Client Usage:
     * - Display welcome message with user name
     * - Show email verification banner if not verified
     * - Personalize UI based on account type
     * - Check roles for client-side feature flags
     */
    private UserResponse user;
    
    /**
     * Check if the user's email is verified.
     * Convenience method for client-side logic.
     * 
     * @return true if email is verified
     */
    public boolean isEmailVerified() {
        return user != null && Boolean.TRUE.equals(user.getEmailVerified());
    }
    
    /**
     * Check if the user has a premium account.
     * Convenience method for client-side logic.
     * 
     * @return true if account is premium
     */
    public boolean isPremiumAccount() {
        return user != null && "PREMIUM".equals(user.getAccountType().name());
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

