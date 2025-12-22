package com.linkedin.user.dto.auth;

import com.linkedin.user.model.AccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user registration request.
 * 
 * Purpose:
 * Captures user information for creating a new account.
 * Used by POST /api/auth/register endpoint.
 * 
 * Flow:
 * 1. Client sends: POST /api/auth/register with this DTO
 * 2. Controller validates input (@Valid)
 * 3. AuthenticationService:
 *    - Checks if email already exists
 *    - Creates user via UserFactory (hashes password)
 *    - Validates via Strategy Pattern (BASIC or PREMIUM validation)
 *    - Saves user to database
 *    - Generates access token + refresh token
 * 4. Returns RegisterResponse (same as LoginResponse)
 * 
 * Differences from CreateUserRequest:
 * - This is for public registration (/api/auth/register)
 * - CreateUserRequest is for admin user creation (/api/users)
 * - Both can coexist for different use cases
 * - This one auto-logs in user after registration
 * 
 * Validation Rules:
 * - Email: Required, valid format, must be unique
 * - Password: Required, min 8 characters, strength validated at service layer
 * - Name: Required, 2-255 characters
 * - AccountType: Optional, defaults to BASIC
 * - For PREMIUM accounts, headline/summary/location are required (validated at service layer)
 * 
 * Security Notes:
 * - Password is in plain text here (HTTPS encrypts in transit)
 * - Password is hashed with BCrypt before storing
 * - Never log this object (contains password)
 * - Email uniqueness checked before creating user
 * 
 * Example Request (BASIC User):
 * POST /api/auth/register
 * {
 *   "email": "john@example.com",
 *   "password": "SecurePass123",
 *   "name": "John Doe"
 * }
 * 
 * Example Request (PREMIUM User):
 * POST /api/auth/register
 * {
 *   "email": "jane@example.com",
 *   "password": "SecurePass456",
 *   "name": "Jane Smith",
 *   "accountType": "PREMIUM",
 *   "headline": "Senior Software Engineer at Google",
 *   "summary": "10 years of experience in distributed systems...",
 *   "location": "San Francisco, CA"
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
 * Example Response (Email Exists):
 * {
 *   "errorCode": "USER_EMAIL_EXISTS",
 *   "message": "Email address already registered",
 *   "status": 400
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    
    /**
     * User's email address (will be used as username).
     * 
     * Validation:
     * - @NotBlank: Cannot be null, empty, or whitespace
     * - @Email: Must be valid email format
     * - Must be unique (checked at service layer)
     * 
     * Case Sensitivity:
     * - Emails are case-insensitive
     * - Converted to lowercase before storing
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
     * - Additional validation at service layer:
     *   - At least 1 uppercase letter
     *   - At least 1 lowercase letter
     *   - At least 1 digit
     * 
     * Security:
     * - Sent over HTTPS (encrypted in transit)
     * - Hashed with BCrypt before storing (work factor 10)
     * - Never stored in plain text
     * - Never logged
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
    
    /**
     * User's full name.
     * 
     * Validation:
     * - @NotBlank: Cannot be null, empty, or whitespace
     * - @Size(min = 2, max = 255): Between 2 and 255 characters
     * 
     * Examples:
     * - "John Doe"
     * - "Jane Smith"
     * - "李明" (international names supported)
     */
    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;
    
    /**
     * Account type: BASIC or PREMIUM.
     * 
     * Default: BASIC (if not provided)
     * 
     * BASIC Account:
     * - Free tier
     * - Limited features
     * - Only email, password, name required
     * 
     * PREMIUM Account:
     * - Paid tier
     * - Advanced features
     * - Requires: email, password, name, headline, summary, location
     * 
     * Validation:
     * - If PREMIUM, additional fields are required (checked by Strategy Pattern)
     */
    @Builder.Default
    private AccountType accountType = AccountType.BASIC;
    
    // ==================== PREMIUM Account Fields (Optional for BASIC) ====================
    
    /**
     * Professional headline.
     * 
     * Required for PREMIUM accounts.
     * Optional for BASIC accounts.
     * 
     * PREMIUM Validation:
     * - Min 10 characters
     * - Max 255 characters
     * 
     * Example: "Senior Software Engineer at Google"
     */
    @Size(max = 255, message = "Headline cannot exceed 255 characters")
    private String headline;
    
    /**
     * Profile summary/bio.
     * 
     * Required for PREMIUM accounts.
     * Optional for BASIC accounts.
     * 
     * PREMIUM Validation:
     * - Min 50 characters
     * - Max 2000 characters
     * 
     * Example: "10 years of experience in distributed systems..."
     */
    @Size(max = 2000, message = "Summary cannot exceed 2000 characters")
    private String summary;
    
    /**
     * Location/City.
     * 
     * Required for PREMIUM accounts.
     * Optional for BASIC accounts.
     * 
     * PREMIUM Validation:
     * - Min 3 characters
     * - Max 255 characters
     * 
     * Example: "San Francisco, CA"
     */
    @Size(max = 255, message = "Location cannot exceed 255 characters")
    private String location;
    
    /**
     * Phone number (optional for all accounts).
     * 
     * Format validation at service layer.
     * Example: "+1-555-0123"
     */
    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String phoneNumber;
    
    /**
     * Industry/field (optional for all accounts).
     * 
     * Example: "Technology", "Healthcare", "Finance"
     */
    @Size(max = 100, message = "Industry cannot exceed 100 characters")
    private String industry;
    
    /**
     * Current job title (optional for all accounts).
     * 
     * Example: "Senior Software Engineer"
     */
    @Size(max = 255, message = "Current job title cannot exceed 255 characters")
    private String currentJobTitle;
    
    /**
     * Current company name (optional for all accounts).
     * 
     * Example: "Google"
     */
    @Size(max = 255, message = "Current company cannot exceed 255 characters")
    private String currentCompany;
    
    /**
     * String representation for logging (EXCLUDES password for security).
     * 
     * @return Safe string representation
     */
    @Override
    public String toString() {
        return "RegisterRequest{" +
                "email='" + email + '\'' +
                ", password='[PROTECTED]'" +
                ", name='" + name + '\'' +
                ", accountType=" + accountType +
                ", headline='" + headline + '\'' +
                '}';
    }
}

