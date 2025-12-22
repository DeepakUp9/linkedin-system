package com.linkedin.user.controller;

import com.linkedin.common.dto.ApiResponse;
import com.linkedin.user.dto.CreateUserRequest;
import com.linkedin.user.dto.UpdateUserRequest;
import com.linkedin.user.dto.UserResponse;
import com.linkedin.user.model.AccountType;
import com.linkedin.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST Controller for User management operations.
 * 
 * This controller provides HTTP endpoints for creating, reading, updating,
 * and deleting users. It acts as the presentation layer, handling HTTP
 * requests and delegating business logic to the UserService.
 * 
 * Design Principle: Thin Controller Pattern
 * 
 * Controllers should be "thin" - they only handle HTTP concerns:
 * - Request/response mapping
 * - Input validation (@Valid)
 * - HTTP status codes
 * - API documentation
 * 
 * All business logic is in the service layer.
 * 
 * Base URL: /api/users
 * 
 * Endpoints Overview:
 * 
 * Create Operations:
 * - POST   /api/users                    - Create BASIC user
 * - POST   /api/users/premium            - Create PREMIUM user
 * 
 * Read Operations:
 * - GET    /api/users/{id}               - Get user by ID
 * - GET    /api/users/email/{email}      - Get user by email
 * - GET    /api/users                    - Get all users (paginated)
 * - GET    /api/users/active             - Get active users
 * - GET    /api/users/type/{accountType} - Get users by account type
 * - GET    /api/users/search             - Search users
 * - GET    /api/users/recent             - Get recent users
 * 
 * Update Operations:
 * - PATCH  /api/users/{id}               - Update user profile
 * - POST   /api/users/{id}/upgrade       - Upgrade to premium
 * 
 * Delete Operations:
 * - DELETE /api/users/{id}               - Delete (deactivate) user
 * 
 * HTTP Method Usage:
 * - POST: Create new resources
 * - GET: Retrieve resources (read-only, no side effects)
 * - PATCH: Partial update (only specified fields)
 * - PUT: Full replacement (not used here)
 * - DELETE: Remove resource (soft delete in our case)
 * 
 * Response Format:
 * All successful responses are wrapped in ApiResponse<T>:
 * {
 *   "success": true,
 *   "message": "User created successfully",
 *   "data": { ... UserResponse ... },
 *   "timestamp": "2025-12-20T18:42:22"
 * }
 * 
 * Error responses are handled by GlobalExceptionHandler:
 * {
 *   "errorCode": "VALIDATION_ERROR",
 *   "message": "Email already exists",
 *   "status": 400,
 *   "timestamp": "2025-12-20T18:42:22",
 *   "path": "/api/users"
 * }
 * 
 * Input Validation:
 * - @Valid triggers Bean Validation (@NotNull, @Email, @Size)
 * - Validation failures → HTTP 400 with field-level errors
 * - Handled by GlobalExceptionHandler
 * 
 * Pagination:
 * - Uses Spring Data's Pageable interface
 * - Query parameters: ?page=0&size=20&sort=createdAt,desc
 * - Default: page=0, size=20, sort=id,asc
 * 
 * OpenAPI/Swagger Documentation:
 * - @Tag: Groups endpoints in Swagger UI
 * - @Operation: Describes endpoint purpose
 * - @Parameter: Describes request parameters
 * - @ApiResponse: Documents response codes
 * 
 * Access Swagger UI:
 * - http://localhost:8080/swagger-ui.html
 * - Interactive API documentation
 * - Try-it-out functionality
 * 
 * Security Considerations:
 * - Input validation prevents injection attacks
 * - No sensitive data in logs (passwords filtered)
 * - Rate limiting should be added (API Gateway)
 * - Authentication/Authorization (will be added in security phase)
 * 
 * Testing Strategy:
 * - Unit Tests: Mock UserService, test controller logic
 * - Integration Tests: Use MockMvc, test full request/response
 * - End-to-End Tests: Real HTTP calls with TestRestTemplate
 * 
 * @see UserService
 * @see ApiResponse
 * @see GlobalExceptionHandler
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "APIs for managing user accounts")
public class UserController {

    private final UserService userService;

    /**
     * Creates a new user with BASIC account type.
     * 
     * Endpoint: POST /api/users
     * 
     * Request Body Example:
     * {
     *   "email": "john@example.com",
     *   "password": "SecurePass123",
     *   "name": "John Doe",
     *   "headline": "Software Developer",  // Optional
     *   "location": "San Francisco"         // Optional
     * }
     * 
     * Response (HTTP 201 Created):
     * {
     *   "success": true,
     *   "message": "User created successfully",
     *   "data": {
     *     "id": 1,
     *     "email": "john@example.com",
     *     "name": "John Doe",
     *     "accountType": "BASIC",
     *     "isActive": true,
     *     "emailVerified": false,
     *     "createdAt": "2025-12-20T18:42:22"
     *   },
     *   "timestamp": "2025-12-20T18:42:22"
     * }
     * 
     * Error Responses:
     * - 400 Bad Request: Validation failed (invalid email, weak password)
     * - 400 Bad Request: Email already exists
     * - 500 Internal Server Error: Unexpected error
     * 
     * Process Flow:
     * 1. Spring validates request body (@Valid)
     * 2. Controller calls userService.createUser()
     * 3. Service orchestrates: Factory → Strategy → Repository → Mapper
     * 4. Service returns UserResponse DTO
     * 5. Controller wraps in ApiResponse
     * 6. Spring serializes to JSON and returns HTTP 201
     * 
     * @param request CreateUserRequest with user data
     * @return ApiResponse containing created UserResponse
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a new BASIC user",
        description = "Creates a new user account with BASIC tier. Only email, password, and name are required."
    )
    public ApiResponse<UserResponse> createUser(
        @Valid @RequestBody CreateUserRequest request
    ) {
        log.info("Received request to create user with email: {}", request.getEmail());
        
        UserResponse createdUser = userService.createUser(request);
        
        log.info("User created successfully with ID: {}", createdUser.getId());
        return ApiResponse.success(createdUser, "User created successfully");
    }

    /**
     * Creates a new user with PREMIUM account type.
     * 
     * Endpoint: POST /api/users/premium
     * 
     * PREMIUM users have stricter requirements:
     * - Headline: Required, minimum 10 characters
     * - Summary: Required, minimum 50 characters
     * - Location: Required
     * 
     * Request Body Example:
     * {
     *   "email": "jane@example.com",
     *   "password": "SecurePass456",
     *   "name": "Jane Smith",
     *   "headline": "Senior Software Engineer at Google",
     *   "summary": "10 years of experience in distributed systems, cloud architecture...",
     *   "location": "New York, NY"
     * }
     * 
     * Response (HTTP 201 Created):
     * {
     *   "success": true,
     *   "message": "Premium user created successfully",
     *   "data": {
     *     "id": 2,
     *     "email": "jane@example.com",
     *     "name": "Jane Smith",
     *     "accountType": "PREMIUM",
     *     "headline": "Senior Software Engineer at Google",
     *     "summary": "10 years of experience...",
     *     "location": "New York, NY",
     *     "createdAt": "2025-12-20T18:42:22"
     *   }
     * }
     * 
     * Error Responses:
     * - 400 Bad Request: Premium requirements not met (headline too short, etc.)
     * - 400 Bad Request: Email already exists
     * 
     * @param request CreateUserRequest with premium-required fields
     * @return ApiResponse containing created premium UserResponse
     */
    @PostMapping("/premium")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a new PREMIUM user",
        description = "Creates a new user account with PREMIUM tier. Requires headline, summary, and location."
    )
    public ApiResponse<UserResponse> createPremiumUser(
        @Valid @RequestBody CreateUserRequest request
    ) {
        log.info("Received request to create PREMIUM user with email: {}", request.getEmail());
        
        UserResponse createdUser = userService.createPremiumUser(request);
        
        log.info("Premium user created successfully with ID: {}", createdUser.getId());
        return ApiResponse.success(createdUser, "Premium user created successfully");
    }

    /**
     * Retrieves a user by their ID.
     * 
     * Endpoint: GET /api/users/{id}
     * 
     * Example: GET /api/users/1
     * 
     * Response (HTTP 200 OK):
     * {
     *   "success": true,
     *   "message": "User retrieved successfully",
     *   "data": {
     *     "id": 1,
     *     "email": "john@example.com",
     *     "name": "John Doe",
     *     "accountType": "BASIC"
     *   }
     * }
     * 
     * Error Responses:
     * - 404 Not Found: User with specified ID doesn't exist
     * 
     * Use Cases:
     * - View user profile
     * - Load user details for editing
     * - Check user existence
     * 
     * @param id User ID (path variable)
     * @return ApiResponse containing UserResponse
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get user by ID",
        description = "Retrieves a user's profile by their unique identifier"
    )
    public ApiResponse<UserResponse> getUserById(
        @Parameter(description = "User ID", required = true)
        @PathVariable Long id
    ) {
        log.info("Received request to get user by ID: {}", id);
        
        UserResponse user = userService.getUserById(id);
        
        log.info("User retrieved successfully: {}", user.getEmail());
        return ApiResponse.success(user, "User retrieved successfully");
    }

    /**
     * Retrieves a user by their email address.
     * 
     * Endpoint: GET /api/users/email/{email}
     * 
     * Example: GET /api/users/email/john@example.com
     * 
     * Response (HTTP 200 OK):
     * {
     *   "success": true,
     *   "message": "User retrieved successfully",
     *   "data": { ... user data ... }
     * }
     * 
     * Error Responses:
     * - 404 Not Found: User with specified email doesn't exist
     * 
     * Use Cases:
     * - Login lookup
     * - Check if email is already registered
     * - Profile search by email
     * 
     * @param email User email (path variable, URL-encoded if contains special chars)
     * @return ApiResponse containing UserResponse
     */
    @GetMapping("/email/{email}")
    @Operation(
        summary = "Get user by email",
        description = "Retrieves a user's profile by their email address"
    )
    public ApiResponse<UserResponse> getUserByEmail(
        @Parameter(description = "User email address", required = true)
        @PathVariable String email
    ) {
        log.info("Received request to get user by email: {}", email);
        
        UserResponse user = userService.getUserByEmail(email);
        
        log.info("User retrieved successfully: {}", user.getId());
        return ApiResponse.success(user, "User retrieved successfully");
    }

    /**
     * Retrieves all users with pagination support.
     * 
     * Endpoint: GET /api/users
     * 
     * Query Parameters:
     * - page: Page number (0-indexed, default: 0)
     * - size: Page size (default: 20)
     * - sort: Sort field and direction (default: id,asc)
     * 
     * Examples:
     * - GET /api/users                           → First page, 20 items
     * - GET /api/users?page=2&size=10            → Page 2, 10 items per page
     * - GET /api/users?sort=createdAt,desc       → Sort by creation date descending
     * - GET /api/users?page=1&size=50&sort=name  → Page 1, 50 items, sorted by name
     * 
     * Response (HTTP 200 OK):
     * {
     *   "success": true,
     *   "message": "Users retrieved successfully",
     *   "data": {
     *     "content": [ {...}, {...}, {...} ],  // Array of users
     *     "pageable": {
     *       "pageNumber": 0,
     *       "pageSize": 20,
     *       "sort": { ... }
     *     },
     *     "totalElements": 100,      // Total users in database
     *     "totalPages": 5,            // Total pages
     *     "last": false,              // Is this the last page?
     *     "first": true,              // Is this the first page?
     *     "numberOfElements": 20      // Items in current page
     *   }
     * }
     * 
     * Performance:
     * - Only loads requested page from database
     * - Efficient for large datasets
     * - Database performs LIMIT/OFFSET query
     * 
     * @param pageable Pagination parameters (auto-bound from query params)
     * @return ApiResponse containing Page of UserResponse
     */
    @GetMapping
    @Operation(
        summary = "Get all users (paginated)",
        description = "Retrieves all users with pagination and sorting support"
    )
    public ApiResponse<Page<UserResponse>> getAllUsers(
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC)
        Pageable pageable
    ) {
        log.info("Received request to get all users with pagination: {}", pageable);
        
        Page<UserResponse> users = userService.getAllUsers(pageable);
        
        log.info("Retrieved {} users out of {} total", 
            users.getNumberOfElements(), 
            users.getTotalElements());
        return ApiResponse.success(users, "Users retrieved successfully");
    }

    /**
     * Retrieves all active users (not deactivated).
     * 
     * Endpoint: GET /api/users/active
     * 
     * Example: GET /api/users/active
     * 
     * Response (HTTP 200 OK):
     * {
     *   "success": true,
     *   "message": "Active users retrieved successfully",
     *   "data": [
     *     { "id": 1, "email": "user1@example.com", "isActive": true },
     *     { "id": 2, "email": "user2@example.com", "isActive": true }
     *   ]
     * }
     * 
     * Use Cases:
     * - Get list of users who can login
     * - Exclude suspended/deleted accounts
     * - Active user analytics
     * 
     * Note: Returns List (not paginated). Consider adding pagination for production.
     * 
     * @return ApiResponse containing List of active UserResponse
     */
    @GetMapping("/active")
    @Operation(
        summary = "Get all active users",
        description = "Retrieves all users with isActive=true"
    )
    public ApiResponse<List<UserResponse>> getActiveUsers() {
        log.info("Received request to get all active users");
        
        List<UserResponse> users = userService.getActiveUsers();
        
        log.info("Retrieved {} active users", users.size());
        return ApiResponse.success(users, "Active users retrieved successfully");
    }

    /**
     * Retrieves users by account type (BASIC or PREMIUM) with pagination.
     * 
     * Endpoint: GET /api/users/type/{accountType}
     * 
     * Path Parameters:
     * - accountType: BASIC or PREMIUM (case-sensitive)
     * 
     * Query Parameters:
     * - page, size, sort (same as getAllUsers)
     * 
     * Examples:
     * - GET /api/users/type/BASIC?page=0&size=10
     * - GET /api/users/type/PREMIUM?sort=createdAt,desc
     * 
     * Response (HTTP 200 OK):
     * {
     *   "success": true,
     *   "message": "Users retrieved successfully",
     *   "data": {
     *     "content": [ ... array of PREMIUM users ... ],
     *     "totalElements": 50
     *   }
     * }
     * 
     * Use Cases:
     * - Get all premium users for targeted campaigns
     * - Count users by tier for analytics
     * - Filter users by subscription level
     * 
     * @param accountType BASIC or PREMIUM (path variable)
     * @param pageable Pagination parameters
     * @return ApiResponse containing Page of UserResponse
     */
    @GetMapping("/type/{accountType}")
    @Operation(
        summary = "Get users by account type",
        description = "Retrieves users filtered by account type (BASIC or PREMIUM)"
    )
    public ApiResponse<Page<UserResponse>> getUsersByAccountType(
        @Parameter(description = "Account type: BASIC or PREMIUM", required = true)
        @PathVariable AccountType accountType,
        @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        log.info("Received request to get users by account type: {}", accountType);
        
        Page<UserResponse> users = userService.getUsersByAccountType(accountType, pageable);
        
        log.info("Retrieved {} {} users", users.getTotalElements(), accountType);
        return ApiResponse.success(users, "Users retrieved successfully");
    }

    /**
     * Searches for users by name, email, or headline.
     * 
     * Endpoint: GET /api/users/search
     * 
     * Query Parameters:
     * - q: Search term (searches in name, email, headline)
     * - type: Filter by account type (optional: BASIC or PREMIUM)
     * - page, size, sort: Pagination
     * 
     * Examples:
     * - GET /api/users/search?q=john
     * - GET /api/users/search?q=developer&type=PREMIUM
     * - GET /api/users/search?q=example.com&page=0&size=10
     * 
     * Response (HTTP 200 OK):
     * {
     *   "success": true,
     *   "message": "Search completed successfully",
     *   "data": {
     *     "content": [ ... matching users ... ],
     *     "totalElements": 15
     *   }
     * }
     * 
     * Search Logic:
     * - Case-insensitive
     * - LIKE '%searchTerm%' (substring match)
     * - Searches: name, email, headline fields
     * - Only active users returned
     * 
     * Performance Note:
     * - LIKE with leading wildcard can't use index
     * - Consider full-text search (Elasticsearch) for production
     * 
     * @param searchTerm Search query string
     * @param accountType Optional filter by account type
     * @param pageable Pagination parameters
     * @return ApiResponse containing Page of matching UserResponse
     */
    @GetMapping("/search")
    @Operation(
        summary = "Search users",
        description = "Searches users by name, email, or headline with optional account type filter"
    )
    public ApiResponse<Page<UserResponse>> searchUsers(
        @Parameter(description = "Search term", required = true)
        @RequestParam("q") String searchTerm,
        @Parameter(description = "Filter by account type (optional)")
        @RequestParam(value = "type", required = false) AccountType accountType,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        log.info("Received search request: term='{}', type={}", searchTerm, accountType);
        
        Page<UserResponse> users = userService.searchUsers(searchTerm, accountType, pageable);
        
        log.info("Search found {} users", users.getTotalElements());
        return ApiResponse.success(users, "Search completed successfully");
    }

    /**
     * Retrieves recently created users (created within specified days).
     * 
     * Endpoint: GET /api/users/recent
     * 
     * Query Parameters:
     * - days: Number of days to look back (default: 7)
     * 
     * Examples:
     * - GET /api/users/recent              → Users created in last 7 days
     * - GET /api/users/recent?days=30      → Users created in last 30 days
     * - GET /api/users/recent?days=1       → Users created today
     * 
     * Response (HTTP 200 OK):
     * {
     *   "success": true,
     *   "message": "Recent users retrieved successfully",
     *   "data": [
     *     { "id": 5, "email": "new1@example.com", "createdAt": "2025-12-19T10:00:00" },
     *     { "id": 6, "email": "new2@example.com", "createdAt": "2025-12-20T14:30:00" }
     *   ]
     * }
     * 
     * Use Cases:
     * - "New members" section
     * - Welcome email campaigns
     * - User growth analytics
     * - Onboarding tracking
     * 
     * Note: Returns List (not paginated). Consider adding pagination for production.
     * 
     * @param days Number of days to look back (default: 7)
     * @return ApiResponse containing List of recent UserResponse
     */
    @GetMapping("/recent")
    @Operation(
        summary = "Get recently created users",
        description = "Retrieves users created within the specified number of days"
    )
    public ApiResponse<List<UserResponse>> getRecentUsers(
        @Parameter(description = "Number of days to look back (default: 7)")
        @RequestParam(value = "days", defaultValue = "7") int days
    ) {
        log.info("Received request to get users created in last {} days", days);
        
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<UserResponse> users = userService.getRecentUsers(since);
        
        log.info("Retrieved {} recent users", users.size());
        return ApiResponse.success(users, "Recent users retrieved successfully");
    }

    /**
     * Updates a user's profile (PATCH - partial update).
     * 
     * Endpoint: PATCH /api/users/{id}
     * 
     * Path Parameters:
     * - id: User ID to update
     * 
     * Request Body (all fields optional):
     * {
     *   "name": "Jane Doe",
     *   "headline": "Senior Engineer",
     *   "location": "Seattle, WA"
     * }
     * 
     * PATCH Semantics:
     * - Only provided fields are updated
     * - Null fields in request are IGNORED (not set to null)
     * - Preserves all other existing data
     * 
     * Example:
     * Before: {id: 1, name: "John", headline: "Developer", location: "SF"}
     * Request: {name: "Jane"}
     * After: {id: 1, name: "Jane", headline: "Developer", location: "SF"}
     * 
     * Response (HTTP 200 OK):
     * {
     *   "success": true,
     *   "message": "User updated successfully",
     *   "data": { ... updated user data ... }
     * }
     * 
     * Error Responses:
     * - 400 Bad Request: Validation failed
     * - 404 Not Found: User doesn't exist
     * - 400 Bad Request: Updated profile doesn't meet requirements (for PREMIUM)
     * 
     * Business Rules:
     * - Can't update: id, email, password, accountType (use dedicated endpoints)
     * - PREMIUM users must maintain complete profile
     * - Profile re-validated after update
     * 
     * @param id User ID to update
     * @param request UpdateUserRequest with fields to update
     * @return ApiResponse containing updated UserResponse
     */
    @PatchMapping("/{id}")
    @Operation(
        summary = "Update user profile",
        description = "Partially updates a user's profile. Only provided fields are updated."
    )
    public ApiResponse<UserResponse> updateUser(
        @Parameter(description = "User ID", required = true)
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        log.info("Received request to update user: {}", id);
        
        UserResponse updatedUser = userService.updateUser(id, request);
        
        log.info("User updated successfully: {}", updatedUser.getId());
        return ApiResponse.success(updatedUser, "User updated successfully");
    }

    /**
     * Deletes a user (soft delete - deactivates account).
     * 
     * Endpoint: DELETE /api/users/{id}
     * 
     * Path Parameters:
     * - id: User ID to delete
     * 
     * Example: DELETE /api/users/1
     * 
     * Response (HTTP 204 No Content):
     * - Empty response body
     * - HTTP 204 indicates successful deletion
     * 
     * Soft Delete:
     * - Sets isActive = false
     * - Preserves all user data
     * - User can't login
     * - Profile hidden from search
     * - Can be reactivated by admin
     * 
     * Hard Delete (NOT used):
     * - Permanently removes from database
     * - Loss of historical data
     * - Breaks foreign key relationships
     * 
     * Error Responses:
     * - 404 Not Found: User doesn't exist
     * 
     * Use Cases:
     * - User requests account deletion
     * - Admin suspends account
     * - Compliance (GDPR right to be forgotten - additional steps needed)
     * 
     * @param id User ID to delete
     * @return ApiResponse with success message
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Delete user account",
        description = "Soft deletes a user by setting isActive=false. Data is preserved."
    )
    public ApiResponse<Void> deleteUser(
        @Parameter(description = "User ID", required = true)
        @PathVariable Long id
    ) {
        log.info("Received request to delete user: {}", id);
        
        userService.deleteUser(id);
        
        log.info("User deactivated successfully: {}", id);
        return ApiResponse.success(null, "User deactivated successfully");
    }

    /**
     * Upgrades a user from BASIC to PREMIUM.
     * 
     * Endpoint: POST /api/users/{id}/upgrade
     * 
     * Path Parameters:
     * - id: User ID to upgrade
     * 
     * Example: POST /api/users/1/upgrade
     * 
     * Response (HTTP 200 OK):
     * {
     *   "success": true,
     *   "message": "User upgraded to PREMIUM successfully",
     *   "data": {
     *     "id": 1,
     *     "email": "john@example.com",
     *     "accountType": "PREMIUM"  ← Changed from BASIC
     *   }
     * }
     * 
     * Business Rules:
     * 1. User must currently be BASIC
     * 2. Profile must meet PREMIUM requirements:
     *    - Headline: 10+ characters
     *    - Summary: 50+ characters
     *    - Location: Present
     * 3. If requirements not met, returns 400 with helpful error message
     * 
     * Error Responses:
     * - 404 Not Found: User doesn't exist
     * - 409 Conflict: User already PREMIUM
     * - 400 Bad Request: Profile doesn't meet PREMIUM requirements
     * 
     * Use Cases:
     * - User purchases premium subscription
     * - Admin grants premium access
     * - Promotional upgrade
     * 
     * Next Steps After Upgrade:
     * - Payment processing (not implemented yet)
     * - Send confirmation email
     * - Enable premium features
     * - Update billing records
     * 
     * @param id User ID to upgrade
     * @return ApiResponse containing upgraded UserResponse
     */
    @PostMapping("/{id}/upgrade")
    @Operation(
        summary = "Upgrade user to PREMIUM",
        description = "Upgrades a BASIC user to PREMIUM tier. Profile must meet PREMIUM requirements."
    )
    public ApiResponse<UserResponse> upgradeToPremium(
        @Parameter(description = "User ID", required = true)
        @PathVariable Long id
    ) {
        log.info("Received request to upgrade user to PREMIUM: {}", id);
        
        UserResponse upgradedUser = userService.upgradeToPremium(id);
        
        log.info("User upgraded to PREMIUM successfully: {}", upgradedUser.getId());
        return ApiResponse.success(upgradedUser, "User upgraded to PREMIUM successfully");
    }
}

