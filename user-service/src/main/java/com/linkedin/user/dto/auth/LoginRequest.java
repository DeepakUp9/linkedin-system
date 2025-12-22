package com.linkedin.user.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user login request.
 * 
 * Purpose:
 * Captures user credentials for authentication.
 * Used by POST /api/auth/login endpoint.
 * 
 * Flow:
 * 1. Client sends: POST /api/auth/login with this DTO
 * 2. Controller validates input (@Valid)
 * 3. AuthenticationService authenticates user
 * 4. If successful, returns LoginResponse with tokens
 * 5. If failed, returns 401 Unauthorized
 * 
 * Validation Rules:
 * - Email: Required, valid format
 * - Password: Required, min 8 characters
 * 
 * Security Notes:
 * - Password is in plain text here (HTTPS encrypts in transit)
 * - Password is hashed before checking database
 * - Never log this object (contains password)
 * - Never store this object (short-lived request data)
 * 
 * Example Request:
 * POST /api/auth/login
 * {
 *   "email": "john@example.com",
 *   "password": "SecurePass123"
 * }
 * 
 * Example Response (Success):
 * {
 *   "success": true,
 *   "data": {
 *     "accessToken": "eyJhbGci...",
 *     "refreshToken": "550e8400-...",
 *     "tokenType": "Bearer",
 *     "expiresIn": 900,
 *     "user": { ... }
 *   }
 * }
 * 
 * Example Response (Failure):
 * {
 *   "errorCode": "INVALID_CREDENTIALS",
 *   "message": "Invalid email or password",
 *   "status": 401
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    /**
     * User's email address (used as username).
     * 
     * Validation:
     * - @NotBlank: Cannot be null, empty, or whitespace
     * - @Email: Must be valid email format (e.g., user@example.com)
     * 
     * Examples:
     * - Valid: "john@example.com", "jane.doe@company.co.uk"
     * - Invalid: "notanemail", "user@", "@example.com", ""
     * 
     * Case Sensitivity:
     * - Emails are case-insensitive in our system
     * - We convert to lowercase before checking database
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;
    
    /**
     * User's password in plain text.
     * 
     * Validation:
     * - @NotBlank: Cannot be null, empty, or whitespace
     * - @Size(min = 8): Minimum 8 characters
     * 
     * Security Notes:
     * - Sent over HTTPS (encrypted in transit)
     * - Never stored in plain text
     * - Compared with BCrypt hash in database
     * - Never logged or cached
     * 
     * Password Requirements (enforced at registration):
     * - Minimum 8 characters
     * - At least 1 uppercase letter
     * - At least 1 lowercase letter
     * - At least 1 digit
     * 
     * Note: At login, we only check length (full validation done at registration)
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
    
    /**
     * String representation for logging (EXCLUDES password for security).
     * 
     * @return Safe string representation
     */
    @Override
    public String toString() {
        return "LoginRequest{" +
                "email='" + email + '\'' +
                ", password='[PROTECTED]'" +
                '}';
    }
}

