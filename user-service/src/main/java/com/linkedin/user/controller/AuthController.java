package com.linkedin.user.controller;

import com.linkedin.common.dto.ApiResponse;
import com.linkedin.user.dto.UserResponse;
import com.linkedin.user.dto.auth.*;
import com.linkedin.user.mapper.UserMapper;
import com.linkedin.user.model.User;
import com.linkedin.user.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for authentication operations.
 * 
 * Purpose:
 * This controller provides HTTP endpoints for user authentication and authorization.
 * It exposes the authentication functionality built in AuthenticationService via REST API.
 * 
 * Base Path: /api/auth
 * 
 * Endpoints:
 * 1. POST /api/auth/login - User login
 * 2. POST /api/auth/register - User registration
 * 3. POST /api/auth/refresh - Refresh access token
 * 4. POST /api/auth/logout - Logout (revoke refresh token)
 * 5. GET /api/auth/me - Get current authenticated user
 * 
 * Design Patterns:
 * 
 * 1. Controller Layer Pattern:
 *    - Handles HTTP requests/responses
 *    - Validates input with @Valid
 *    - Delegates business logic to service layer
 *    - Returns consistent response format (ApiResponse)
 * 
 * 2. DTO Pattern:
 *    - Uses request DTOs (LoginRequest, RegisterRequest, etc.)
 *    - Uses response DTOs (LoginResponse, RegisterResponse, etc.)
 *    - Never exposes entity classes directly
 * 
 * 3. Separation of Concerns:
 *    - Controller: HTTP handling, validation, response formatting
 *    - Service: Business logic, orchestration
 *    - Repository: Database access
 * 
 * Response Format:
 * 
 * All endpoints return ApiResponse wrapper for consistency:
 * 
 * Success Response:
 * <pre>
 * {@code
 * {
 *   "success": true,
 *   "message": "Login successful",
 *   "data": {
 *     "accessToken": "eyJhbGci...",
 *     "refreshToken": "550e8400-...",
 *     "user": { ... }
 *   },
 *   "timestamp": "2025-12-20T20:00:00"
 * }
 * }
 * </pre>
 * 
 * Error Response (handled by GlobalExceptionHandler):
 * <pre>
 * {@code
 * {
 *   "errorCode": "INVALID_CREDENTIALS",
 *   "message": "Invalid email or password",
 *   "status": 401,
 *   "path": "/api/auth/login",
 *   "timestamp": "2025-12-20T20:00:00"
 * }
 * }
 * </pre>
 * 
 * HTTP Status Codes:
 * 
 * Success:
 * - 200 OK: Login, refresh, get current user
 * - 201 Created: Register (new user created)
 * - 204 No Content: Logout (no response body)
 * 
 * Client Errors:
 * - 400 Bad Request: Validation error, invalid input
 * - 401 Unauthorized: Invalid credentials, expired token
 * - 404 Not Found: Refresh token not found
 * 
 * Server Errors:
 * - 500 Internal Server Error: Unexpected error
 * 
 * Security:
 * 
 * Public Endpoints (no authentication required):
 * - POST /api/auth/login
 * - POST /api/auth/register
 * - POST /api/auth/refresh
 * 
 * Protected Endpoints (authentication required):
 * - POST /api/auth/logout (optional: can be public)
 * - GET /api/auth/me (requires valid JWT)
 * 
 * Input Validation:
 * 
 * All request DTOs are validated using Bean Validation (@Valid):
 * - @NotBlank: Field cannot be null, empty, or whitespace
 * - @Email: Must be valid email format
 * - @Size: Length constraints
 * 
 * If validation fails:
 * - MethodArgumentNotValidException thrown
 * - GlobalExceptionHandler converts to 400 Bad Request
 * - Returns detailed field-level errors
 * 
 * OpenAPI/Swagger Documentation:
 * 
 * This controller is fully documented with OpenAPI annotations:
 * - @Tag: Groups endpoints under "Authentication"
 * - @Operation: Describes each endpoint
 * - @ApiResponse: Documents response codes and types
 * - @Parameter: Describes request parameters
 * 
 * Access Swagger UI: http://localhost:8080/swagger-ui/index.html
 * 
 * CORS:
 * 
 * For frontend integration, CORS must be configured:
 * - Allow origins: https://your-frontend.com
 * - Allow methods: GET, POST, PUT, DELETE
 * - Allow headers: Authorization, Content-Type
 * - Expose headers: Authorization
 * 
 * Rate Limiting (Future):
 * 
 * Authentication endpoints should be rate-limited:
 * - Login: 5 attempts per minute per IP
 * - Register: 3 attempts per minute per IP
 * - Refresh: 10 attempts per minute per user
 * 
 * Implement using:
 * - Spring Boot Starter Rate Limit
 * - Redis for distributed rate limiting
 * - Bucket4j library
 * 
 * Testing:
 * 
 * Integration Test Example:
 * <pre>
 * {@code
 * @SpringBootTest
 * @AutoConfigureMockMvc
 * class AuthControllerIntegrationTest {
 * 
 *     @Autowired
 *     private MockMvc mockMvc;
 * 
 *     @Test
 *     void testLogin() throws Exception {
 *         mockMvc.perform(post("/api/auth/login")
 *             .contentType(MediaType.APPLICATION_JSON)
 *             .content("{\"email\":\"test@example.com\",\"password\":\"Pass123\"}"))
 *             .andExpect(status().isOk())
 *             .andExpect(jsonPath("$.success").value(true))
 *             .andExpect(jsonPath("$.data.accessToken").exists());
 *     }
 * }
 * }
 * </pre>
 * 
 * @see AuthenticationService
 * @see LoginRequest
 * @see LoginResponse
 * @see RegisterRequest
 * @see RegisterResponse
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "APIs for user authentication and authorization")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserMapper userMapper;

    // ==================== LOGIN ====================

    /**
     * Login endpoint - Authenticates user and returns tokens.
     * 
     * This endpoint validates user credentials and returns JWT access token
     * and refresh token for subsequent API calls.
     * 
     * Request:
     * - Method: POST
     * - Path: /api/auth/login
     * - Content-Type: application/json
     * - Body: LoginRequest (email, password)
     * 
     * Success Response (200 OK):
     * <pre>
     * {@code
     * {
     *   "success": true,
     *   "message": "Login successful",
     *   "data": {
     *     "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
     *     "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
     *     "tokenType": "Bearer",
     *     "expiresIn": 900,
     *     "issuedAt": "2025-12-20T20:00:00",
     *     "user": {
     *       "id": 1,
     *       "email": "john@example.com",
     *       "name": "John Doe",
     *       "accountType": "BASIC",
     *       "roles": ["USER"],
     *       "isActive": true
     *     }
     *   },
     *   "timestamp": "2025-12-20T20:00:00"
     * }
     * }
     * </pre>
     * 
     * Error Responses:
     * 
     * 400 Bad Request (validation error):
     * - Email format invalid
     * - Password too short
     * - Missing required fields
     * 
     * 401 Unauthorized (invalid credentials):
     * - Email doesn't exist
     * - Password doesn't match
     * - Account inactive
     * 
     * cURL Example:
     * <pre>
     * {@code
     * curl -X POST http://localhost:8080/api/auth/login \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "email": "john@example.com",
     *     "password": "Pass123"
     *   }'
     * }
     * </pre>
     * 
     * JavaScript Example:
     * <pre>
     * {@code
     * const response = await fetch('/api/auth/login', {
     *   method: 'POST',
     *   headers: { 'Content-Type': 'application/json' },
     *   body: JSON.stringify({
     *     email: 'john@example.com',
     *     password: 'Pass123'
     *   })
     * });
     * 
     * const data = await response.json();
     * if (data.success) {
     *   localStorage.setItem('accessToken', data.data.accessToken);
     *   localStorage.setItem('refreshToken', data.data.refreshToken);
     *   // Redirect to dashboard
     * }
     * }
     * </pre>
     * 
     * @param request Login credentials (email, password)
     * @return ApiResponse containing LoginResponse with tokens and user info
     */
    @Operation(
        summary = "User login",
        description = "Authenticates user credentials and returns JWT access token and refresh token"
    )
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Login successful",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = LoginResponse.class)
        )
    )
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Invalid input (validation error)",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = com.linkedin.common.dto.ErrorResponse.class)
        )
    )
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Invalid credentials or account inactive",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = com.linkedin.common.dto.ErrorResponse.class)
        )
    )
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        log.info("Login request received for email: {}", request.getEmail());

        LoginResponse loginResponse = authenticationService.login(request);

        log.info("Login successful for user: {}", request.getEmail());

        return ResponseEntity.ok(
            ApiResponse.success(loginResponse, "Login successful")
        );
    }

    // ==================== REGISTER ====================

    /**
     * Register endpoint - Creates new user and returns tokens (auto-login).
     * 
     * This endpoint creates a new user account and automatically logs them in
     * by returning JWT access token and refresh token.
     * 
     * Request:
     * - Method: POST
     * - Path: /api/auth/register
     * - Content-Type: application/json
     * - Body: RegisterRequest (email, password, name, optional fields)
     * 
     * Success Response (201 Created):
     * <pre>
     * {@code
     * {
     *   "success": true,
     *   "message": "Registration successful",
     *   "data": {
     *     "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
     *     "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
     *     "tokenType": "Bearer",
     *     "expiresIn": 900,
     *     "issuedAt": "2025-12-20T20:00:00",
     *     "user": {
     *       "id": 42,
     *       "email": "jane@example.com",
     *       "name": "Jane Smith",
     *       "accountType": "BASIC",
     *       "roles": ["USER"],
     *       "isActive": true,
     *       "emailVerified": false,
     *       "createdAt": "2025-12-20T20:00:00"
     *     }
     *   },
     *   "timestamp": "2025-12-20T20:00:00"
     * }
     * }
     * </pre>
     * 
     * Registration Options:
     * 
     * BASIC Account (default):
     * - Required: email, password, name
     * - Optional: all other fields
     * 
     * PREMIUM Account:
     * - Required: email, password, name, headline, summary, location
     * - Optional: phoneNumber, industry, etc.
     * 
     * Error Responses:
     * 
     * 400 Bad Request:
     * - Email already registered
     * - Validation errors (email format, password length, etc.)
     * - PREMIUM account missing required fields
     * 
     * cURL Example (BASIC):
     * <pre>
     * {@code
     * curl -X POST http://localhost:8080/api/auth/register \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "email": "john@example.com",
     *     "password": "Pass123",
     *     "name": "John Doe"
     *   }'
     * }
     * </pre>
     * 
     * cURL Example (PREMIUM):
     * <pre>
     * {@code
     * curl -X POST http://localhost:8080/api/auth/register \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "email": "jane@example.com",
     *     "password": "Pass456",
     *     "name": "Jane Smith",
     *     "accountType": "PREMIUM",
     *     "headline": "Senior Software Engineer at Google",
     *     "summary": "10 years of experience in distributed systems...",
     *     "location": "San Francisco, CA"
     *   }'
     * }
     * </pre>
     * 
     * @param request Registration data (email, password, name, optional fields)
     * @return ApiResponse containing RegisterResponse with tokens and user info
     */
    @Operation(
        summary = "User registration",
        description = "Creates new user account and returns tokens (auto-login). " +
                     "Supports BASIC (free) and PREMIUM (paid) account types."
    )
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Registration successful",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = RegisterResponse.class)
        )
    )
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Invalid input or email already exists",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = com.linkedin.common.dto.ErrorResponse.class)
        )
    )
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        log.info("Registration request received for email: {}", request.getEmail());

        RegisterResponse registerResponse = authenticationService.register(request);

        log.info("Registration successful for user: {} (ID: {})",
                 request.getEmail(), registerResponse.getUser().getId());

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(registerResponse, "Registration successful"));
    }

    // ==================== REFRESH TOKEN ====================

    /**
     * Refresh token endpoint - Gets new access token using refresh token.
     * 
     * This endpoint validates a refresh token and issues a new access token.
     * If the refresh token is close to expiry, it also rotates the refresh token.
     * 
     * Request:
     * - Method: POST
     * - Path: /api/auth/refresh
     * - Content-Type: application/json
     * - Body: RefreshTokenRequest (refreshToken)
     * 
     * Success Response (200 OK):
     * <pre>
     * {@code
     * // Scenario 1: Refresh token still has > 1 day left
     * {
     *   "success": true,
     *   "message": "Token refreshed successfully",
     *   "data": {
     *     "accessToken": "eyJhbGciOiJIUzUxMiJ9...",  // New access token
     *     "refreshToken": null,  // Keep using same refresh token
     *     "tokenType": "Bearer",
     *     "expiresIn": 900,
     *     "issuedAt": "2025-12-20T20:15:00"
     *   }
     * }
     * 
     * // Scenario 2: Refresh token has < 1 day left (rotation)
     * {
     *   "success": true,
     *   "message": "Token refreshed successfully",
     *   "data": {
     *     "accessToken": "eyJhbGciOiJIUzUxMiJ9...",  // New access token
     *     "refreshToken": "9b2d5f8a-3c4e-41f5-b826-...",  // NEW refresh token
     *     "tokenType": "Bearer",
     *     "expiresIn": 900,
     *     "issuedAt": "2025-12-20T20:15:00"
     *   }
     * }
     * }
     * </pre>
     * 
     * Token Rotation:
     * - If refresh token has < 1 day remaining, server issues new refresh token
     * - Old refresh token is deleted (cannot be reused)
     * - Client MUST update stored refresh token
     * - Extends session for active users
     * 
     * Error Responses:
     * 
     * 404 Not Found:
     * - Refresh token doesn't exist
     * - Token already used/rotated
     * 
     * 401 Unauthorized:
     * - Refresh token expired (> 7 days old)
     * - User account inactive
     * 
     * cURL Example:
     * <pre>
     * {@code
     * curl -X POST http://localhost:8080/api/auth/refresh \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
     *   }'
     * }
     * </pre>
     * 
     * JavaScript Example:
     * <pre>
     * {@code
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
     *     const data = await response.json();
     * 
     *     // Always update access token
     *     localStorage.setItem('accessToken', data.data.accessToken);
     * 
     *     // Update refresh token if rotated
     *     if (data.data.refreshToken) {
     *       localStorage.setItem('refreshToken', data.data.refreshToken);
     *     }
     *   } else {
     *     // Refresh token invalid/expired → redirect to login
     *     window.location.href = '/login';
     *   }
     * }
     * 
     * // Proactively refresh every 14 minutes (before 15 min expiry)
     * setInterval(refreshAccessToken, 14 * 60 * 1000);
     * }
     * </pre>
     * 
     * @param request Refresh token request containing the refresh token
     * @return ApiResponse containing RefreshTokenResponse with new access token
     */
    @Operation(
        summary = "Refresh access token",
        description = "Validates refresh token and returns new access token. " +
                     "May also return new refresh token if rotation occurs (< 1 day remaining)."
    )
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Token refreshed successfully",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = RefreshTokenResponse.class)
        )
    )
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Refresh token not found",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = com.linkedin.common.dto.ErrorResponse.class)
        )
    )
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Refresh token expired or user inactive",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = com.linkedin.common.dto.ErrorResponse.class)
        )
    )
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        log.info("Token refresh request received");

        RefreshTokenResponse refreshResponse = authenticationService.refresh(request);

        log.info("Token refresh successful");

        return ResponseEntity.ok(
            ApiResponse.success(refreshResponse, "Token refreshed successfully")
        );
    }

    // ==================== LOGOUT ====================

    /**
     * Logout endpoint - Invalidates refresh token (single device).
     * 
     * This endpoint deletes the refresh token from the database,
     * preventing it from being used to obtain new access tokens.
     * 
     * Request:
     * - Method: POST
     * - Path: /api/auth/logout
     * - Content-Type: application/json
     * - Body: RefreshTokenRequest (refreshToken)
     * 
     * Success Response (204 No Content):
     * - Empty response body
     * - Token successfully deleted
     * 
     * Logout Behavior:
     * - Deletes specific refresh token from database
     * - Token immediately invalid (cannot refresh access token)
     * - Other devices remain logged in (each has unique token)
     * - Access token still valid until expiry (max 15 minutes)
     * 
     * After Logout:
     * 1. Client should clear stored tokens:
     *    localStorage.removeItem('accessToken');
     *    localStorage.removeItem('refreshToken');
     * 
     * 2. Access token remains valid for up to 15 minutes
     *    - This is acceptable (short expiration)
     *    - For immediate revocation, implement token blacklist
     * 
     * 3. User must login again on this device
     * 
     * cURL Example:
     * <pre>
     * {@code
     * curl -X POST http://localhost:8080/api/auth/logout \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
     *   }'
     * }
     * </pre>
     * 
     * JavaScript Example:
     * <pre>
     * {@code
     * async function logout() {
     *   const refreshToken = localStorage.getItem('refreshToken');
     * 
     *   await fetch('/api/auth/logout', {
     *     method: 'POST',
     *     headers: { 'Content-Type': 'application/json' },
     *     body: JSON.stringify({ refreshToken })
     *   });
     * 
     *   // Clear tokens
     *   localStorage.removeItem('accessToken');
     *   localStorage.removeItem('refreshToken');
     * 
     *   // Redirect to login
     *   window.location.href = '/login';
     * }
     * }
     * </pre>
     * 
     * @param request Refresh token request containing the token to invalidate
     * @return Empty response with 204 No Content status
     */
    @Operation(
        summary = "User logout",
        description = "Invalidates refresh token (single device logout). " +
                     "Access token remains valid until expiry (max 15 minutes)."
    )
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "Logout successful"
    )
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = com.linkedin.common.dto.ErrorResponse.class)
        )
    )
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        log.info("Logout request received");

        authenticationService.logout(request.getRefreshToken());

        log.info("Logout successful");

        return ResponseEntity.noContent().build();
    }

    // ==================== GET CURRENT USER ====================

    /**
     * Get current user endpoint - Returns authenticated user's information.
     * 
     * This endpoint returns the profile information of the currently
     * authenticated user (extracted from JWT token).
     * 
     * Request:
     * - Method: GET
     * - Path: /api/auth/me
     * - Headers: Authorization: Bearer <access_token>
     * 
     * Success Response (200 OK):
     * <pre>
     * {@code
     * {
     *   "success": true,
     *   "message": "Current user retrieved successfully",
     *   "data": {
     *     "id": 1,
     *     "email": "john@example.com",
     *     "name": "John Doe",
     *     "headline": "Full Stack Developer",
     *     "accountType": "BASIC",
     *     "roles": ["USER"],
     *     "isActive": true,
     *     "emailVerified": true,
     *     "createdAt": "2025-12-01T10:00:00"
     *   },
     *   "timestamp": "2025-12-20T20:00:00"
     * }
     * }
     * </pre>
     * 
     * Authentication:
     * - Requires valid JWT access token in Authorization header
     * - JWT is validated by JwtAuthenticationFilter
     * - User is extracted from SecurityContext
     * 
     * Error Responses:
     * 
     * 401 Unauthorized:
     * - No Authorization header
     * - Invalid JWT token
     * - Expired JWT token
     * 
     * cURL Example:
     * <pre>
     * {@code
     * curl -X GET http://localhost:8080/api/auth/me \
     *   -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
     * }
     * </pre>
     * 
     * JavaScript Example:
     * <pre>
     * {@code
     * async function getCurrentUser() {
     *   const accessToken = localStorage.getItem('accessToken');
     * 
     *   const response = await fetch('/api/auth/me', {
     *     headers: {
     *       'Authorization': `Bearer ${accessToken}`
     *     }
     *   });
     * 
     *   if (response.ok) {
     *     const data = await response.json();
     *     return data.data;  // User object
     *   } else if (response.status === 401) {
     *     // Token expired → try refresh
     *     await refreshAccessToken();
     *     return getCurrentUser();  // Retry
     *   }
     * }
     * }
     * </pre>
     * 
     * Use Cases:
     * - Display user profile in UI
     * - Check user roles for client-side logic
     * - Verify JWT is still valid
     * - Reload user data after updates
     * 
     * @return ApiResponse containing UserResponse with current user info
     */
    @Operation(
        summary = "Get current user",
        description = "Returns information about the currently authenticated user. " +
                     "Requires valid JWT access token in Authorization header.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Current user retrieved successfully",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = UserResponse.class)
        )
    )
                @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Unauthorized (invalid or expired token)",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = com.linkedin.common.dto.ErrorResponse.class)
        )
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        log.info("Get current user request received");

        User currentUser = authenticationService.getCurrentUser();
        UserResponse userResponse = userMapper.toResponse(currentUser);

        log.info("Current user retrieved: {} (ID: {})", currentUser.getEmail(), currentUser.getId());

        return ResponseEntity.ok(
            ApiResponse.success(userResponse, "Current user retrieved successfully")
        );
    }
}

