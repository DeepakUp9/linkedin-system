package com.linkedin.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.linkedin.user.model.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for User responses.
 * 
 * This DTO represents user data sent to clients via REST API.
 * It's a clean separation between internal entity structure and external API contract.
 * 
 * Design Principle: DTO Pattern (Data Transfer Object)
 * 
 * Why Use DTOs Instead of Entities?
 * 
 * 1. Security:
 *    - Entities contain sensitive fields (password, audit fields)
 *    - DTOs only expose what's safe for clients
 *    - Prevents accidental data leaks
 * 
 * 2. API Stability:
 *    - Entity changes don't break API contracts
 *    - Can refactor database without changing API
 *    - Versioning: UserResponseV1, UserResponseV2
 * 
 * 3. Performance:
 *    - Only serialize needed fields
 *    - Reduce JSON payload size
 *    - Avoid lazy-loading issues
 * 
 * 4. Flexibility:
 *    - Combine data from multiple entities
 *    - Format dates/enums differently
 *    - Add computed fields
 * 
 * Entity vs DTO Comparison:
 * 
 * User Entity (Internal):
 * <pre>
 * {@code
 * @Entity
 * public class User extends BaseAuditEntity {
 *     private Long id;
 *     private String email;
 *     private String password;          // ← SENSITIVE! Never expose!
 *     private String name;
 *     private AccountType accountType;
 *     private LocalDateTime createdAt;   // ← Audit field
 *     private LocalDateTime updatedAt;   // ← Audit field
 *     private String createdBy;          // ← Internal info
 *     private String updatedBy;          // ← Internal info
 *     // ... JPA annotations, relationships
 * }
 * }
 * </pre>
 * 
 * UserResponse DTO (External):
 * <pre>
 * {@code
 * public class UserResponse {
 *     private Long id;
 *     private String email;
 *     // NO password field! ✅
 *     private String name;
 *     private AccountType accountType;
 *     private LocalDateTime createdAt;   // ← Safe to show (when they joined)
 *     // NO createdBy/updatedBy! ✅ (internal info)
 *     // ... clean, safe fields only
 * }
 * }
 * </pre>
 * 
 * Field Selection Strategy:
 * 
 * Included Fields:
 * - id: Needed for client-side operations
 * - email: Public identifier
 * - name: Public profile info
 * - headline, summary, location: Public profile info
 * - profilePhotoUrl: Public (profile picture)
 * - accountType: Useful for client (show premium badge)
 * - isActive: Client needs to know account status
 * - emailVerified: Client can show "verify email" prompt
 * - phoneNumber: User's own data (if authenticated)
 * - dateOfBirth: User's own data (if authenticated)
 * - industry, currentJobTitle, currentCompany: Public profile
 * - createdAt: When user joined (public info)
 * 
 * Excluded Fields:
 * - password: NEVER expose! Security risk
 * - updatedAt: Internal audit info (not useful to client)
 * - createdBy: Internal audit info (who created the account)
 * - updatedBy: Internal audit info (who last modified)
 * 
 * Jackson Annotations:
 * 
 * @JsonInclude(Include.NON_NULL):
 * - Omits null fields from JSON response
 * - Reduces payload size
 * - Cleaner API responses
 * 
 * Example:
 * <pre>
 * {@code
 * UserResponse response = UserResponse.builder()
 *     .id(1L)
 *     .email("john@example.com")
 *     .name("John Doe")
 *     .headline(null)  // ← Not included in JSON
 *     .build();
 * 
 * JSON:
 * {
 *   "id": 1,
 *   "email": "john@example.com",
 *   "name": "John Doe"
 *   // "headline" field omitted (was null)
 * }
 * }
 * </pre>
 * 
 * @JsonFormat:
 * - Controls date/time serialization format
 * - Ensures consistent format across API
 * - Example: "2025-12-20T17:39:59" (ISO 8601)
 * 
 * Usage Patterns:
 * 
 * 1. Single User Response:
 * <pre>
 * {@code
 * @GetMapping("/api/users/{id}")
 * public ApiResponse<UserResponse> getUser(@PathVariable Long id) {
 *     UserResponse user = userService.getUserById(id);
 *     return ApiResponse.success(user, "User retrieved successfully");
 * }
 * 
 * Response:
 * {
 *   "success": true,
 *   "message": "User retrieved successfully",
 *   "data": {
 *     "id": 1,
 *     "email": "john@example.com",
 *     "name": "John Doe",
 *     "accountType": "PREMIUM"
 *   }
 * }
 * }
 * </pre>
 * 
 * 2. List of Users:
 * <pre>
 * {@code
 * @GetMapping("/api/users")
 * public ApiResponse<List<UserResponse>> getAllUsers() {
 *     List<UserResponse> users = userService.getAllUsers();
 *     return ApiResponse.success(users, "Users retrieved successfully");
 * }
 * }
 * </pre>
 * 
 * 3. Paginated Response:
 * <pre>
 * {@code
 * @GetMapping("/api/users")
 * public ApiResponse<Page<UserResponse>> getUsers(Pageable pageable) {
 *     Page<UserResponse> users = userService.getUsers(pageable);
 *     return ApiResponse.success(users, "Users retrieved successfully");
 * }
 * }
 * </pre>
 * 
 * Security Considerations:
 * 
 * 1. Field-Level Authorization:
 *    - Some fields only visible to owner
 *    - Example: phoneNumber, dateOfBirth
 *    - Service layer checks: if (currentUser != requestedUser) { dto.setPhoneNumber(null); }
 * 
 * 2. Different DTOs for Different Roles:
 *    - PublicUserResponse: Limited fields for public view
 *    - PrivateUserResponse: Full fields for owner
 *    - AdminUserResponse: Includes audit fields for admins
 * 
 * 3. Sensitive Data Handling:
 *    - NEVER include password (even hashed)
 *    - Consider separate endpoint for sensitive fields
 *    - Require re-authentication for sensitive operations
 * 
 * Performance Optimization:
 * 
 * 1. Lazy Loading:
 *    - Entity might have lazy relationships
 *    - DTO prevents N+1 query issues
 *    - Explicitly fetch what's needed
 * 
 * 2. Projection Queries:
 *    - Can fetch directly into DTO from database
 *    - Example: SELECT new UserResponse(u.id, u.email, u.name) FROM User u
 *    - Reduces memory footprint
 * 
 * 3. Caching:
 *    - DTOs are serializable
 *    - Easy to cache in Redis
 *    - No JPA proxy issues
 * 
 * Mapping from Entity:
 * 
 * Manual Mapping (tedious):
 * <pre>
 * {@code
 * UserResponse response = new UserResponse();
 * response.setId(user.getId());
 * response.setEmail(user.getEmail());
 * response.setName(user.getName());
 * // ... 20 more lines
 * }
 * </pre>
 * 
 * MapStruct (automatic):
 * <pre>
 * {@code
 * @Mapper
 * public interface UserMapper {
 *     UserResponse toResponse(User user);
 * }
 * 
 * // Usage:
 * UserResponse response = userMapper.toResponse(user);
 * }
 * </pre>
 * 
 * Best Practices:
 * 
 * 1. Immutability:
 *    - Consider making DTOs immutable (final fields, no setters)
 *    - Prevents accidental modification
 *    - Thread-safe
 * 
 * 2. Validation:
 *    - Request DTOs have validation (@NotNull, @Email)
 *    - Response DTOs typically don't need validation
 *    - Data already validated in entity
 * 
 * 3. Documentation:
 *    - Add @Schema annotations for OpenAPI/Swagger
 *    - Describe each field's purpose
 *    - Provide example values
 * 
 * 4. Versioning:
 *    - Keep old DTOs when creating new versions
 *    - UserResponseV1, UserResponseV2
 *    - Maintain backward compatibility
 * 
 * @see User
 * @see CreateUserRequest
 * @see UpdateUserRequest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    /**
     * User's unique identifier.
     * 
     * Used by client for subsequent operations:
     * - Update profile: PUT /api/users/{id}
     * - Delete account: DELETE /api/users/{id}
     * - View profile: GET /api/users/{id}
     */
    private Long id;

    /**
     * User's email address.
     * 
     * Public identifier used for:
     * - Login credentials
     * - Communication
     * - Profile identification
     */
    private String email;

    /**
     * User's full name.
     * 
     * Displayed on:
     * - Profile page
     * - Posts and comments
     * - Search results
     * - Connection requests
     */
    private String name;

    /**
     * Professional headline (tagline).
     * 
     * Example: "Senior Software Engineer at Google | Cloud Expert"
     * 
     * Displayed:
     * - Below name on profile
     * - In search results
     * - In connection suggestions
     * 
     * Null for BASIC users if not provided.
     * Required for PREMIUM users.
     */
    private String headline;

    /**
     * Professional summary (about section).
     * 
     * Longer description of user's experience, skills, and goals.
     * 
     * Example:
     * "Experienced software engineer with 8+ years in cloud computing.
     *  Passionate about building scalable microservices..."
     * 
     * Null for BASIC users if not provided.
     * Required for PREMIUM users (min 50 characters).
     */
    private String summary;

    /**
     * User's location.
     * 
     * Example: "San Francisco, CA" or "Remote"
     * 
     * Used for:
     * - Job recommendations
     * - Local networking events
     * - Connection suggestions
     * 
     * Null for BASIC users if not provided.
     * Required for PREMIUM users.
     */
    private String location;

    /**
     * URL to user's profile photo.
     * 
     * Example: "https://cdn.linkedin.com/photos/user123.jpg"
     * 
     * Null if user hasn't uploaded photo.
     * Client should display default avatar if null.
     */
    private String profilePhotoUrl;

    /**
     * Account type: BASIC or PREMIUM.
     * 
     * Used by client to:
     * - Show premium badge
     * - Enable premium features
     * - Prompt for upgrade
     */
    private AccountType accountType;

    /**
     * Whether the account is active.
     * 
     * false = account suspended/deactivated
     * true = account active
     * 
     * Client should:
     * - Show "Account suspended" message if false
     * - Prevent login if false
     */
    private Boolean isActive;

    /**
     * Whether the email has been verified.
     * 
     * false = email not verified (show banner)
     * true = email verified
     * 
     * Client should:
     * - Show "Verify your email" banner if false
     * - Provide "Resend verification" button
     */
    private Boolean emailVerified;

    /**
     * User's phone number (optional).
     * 
     * Format: E.164 (e.g., "+14155552671")
     * 
     * Visibility:
     * - Only visible to user themselves
     * - Hidden from other users for privacy
     * - Service layer should filter based on auth
     */
    private String phoneNumber;

    /**
     * User's date of birth (optional).
     * 
     * Visibility:
     * - Only visible to user themselves
     * - Hidden from other users for privacy
     * - Used for age verification
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    /**
     * User's industry (optional).
     * 
     * Example: "Information Technology", "Healthcare", "Finance"
     * 
     * Used for:
     * - Industry-specific recommendations
     * - Job matching
     * - Analytics
     */
    private String industry;

    /**
     * User's current job title (optional).
     * 
     * Example: "Senior Software Engineer", "Product Manager"
     * 
     * Displayed on profile and in search results.
     */
    private String currentJobTitle;

    /**
     * User's current company (optional).
     * 
     * Example: "Google", "Microsoft", "Self-employed"
     * 
     * Displayed on profile and in search results.
     */
    private String currentCompany;

    /**
     * When the user account was created.
     * 
     * Format: ISO 8601 (e.g., "2025-12-20T17:39:59")
     * 
     * Displayed as:
     * - "Member since December 2025"
     * - "Joined 3 months ago"
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    // Note: The following fields from User entity are EXCLUDED:
    // - password: NEVER expose (security risk)
    // - updatedAt: Internal audit info (not useful to client)
    // - createdBy: Internal audit info (who created account)
    // - updatedBy: Internal audit info (who last modified)
}

