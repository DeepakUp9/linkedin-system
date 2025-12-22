package com.linkedin.user.exception;

import com.linkedin.common.dto.ErrorResponse;
import com.linkedin.common.exceptions.BaseException;
import com.linkedin.common.exceptions.BusinessException;
import com.linkedin.common.exceptions.ResourceNotFoundException;
import com.linkedin.common.exceptions.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the User Service.
 * 
 * This class intercepts all exceptions thrown by controllers and services,
 * and converts them into consistent, user-friendly HTTP responses using
 * our standardized ErrorResponse DTO.
 * 
 * Design Pattern: Centralized Exception Handling (Aspect-Oriented Programming)
 * 
 * What is @RestControllerAdvice?
 * 
 * @RestControllerAdvice is a specialized @Component that allows you to handle
 * exceptions across the whole application in one global handling component.
 * 
 * Benefits:
 * 1. Centralized error handling (DRY principle)
 * 2. Consistent error response format
 * 3. Clean controllers (no try-catch blocks)
 * 4. Separation of concerns (business logic vs error handling)
 * 5. Easy to test and maintain
 * 
 * Without Global Exception Handler:
 * 
 * <pre>
 * {@code
 * @RestController
 * public class UserController {
 *     @PostMapping("/users")
 *     public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
 *         try {
 *             UserResponse user = userService.createUser(request);
 *             return ResponseEntity.ok(user);
 *         } catch (ValidationException e) {
 *             // ❌ Duplicated in every endpoint
 *             return ResponseEntity.badRequest()
 *                 .body(Map.of("error", e.getMessage()));
 *         } catch (ResourceNotFoundException e) {
 *             // ❌ Duplicated in every endpoint
 *             return ResponseEntity.notFound().build();
 *         } catch (Exception e) {
 *             // ❌ Duplicated in every endpoint
 *             return ResponseEntity.status(500)
 *                 .body(Map.of("error", "Internal server error"));
 *         }
 *     }
 *     
 *     @GetMapping("/users/{id}")
 *     public ResponseEntity<?> getUser(@PathVariable Long id) {
 *         try {
 *             // ... same try-catch block repeated! ❌
 *         } catch (ResourceNotFoundException e) {
 *             return ResponseEntity.notFound().build();
 *         } catch (Exception e) {
 *             return ResponseEntity.status(500).body(...);
 *         }
 *     }
 * }
 * }
 * </pre>
 * 
 * With Global Exception Handler:
 * 
 * <pre>
 * {@code
 * @RestController
 * public class UserController {
 *     @PostMapping("/users")
 *     public ApiResponse<UserResponse> createUser(@RequestBody CreateUserRequest request) {
 *         // ✅ Clean! No try-catch needed
 *         UserResponse user = userService.createUser(request);
 *         return ApiResponse.success(user, "User created successfully");
 *     }
 *     
 *     @GetMapping("/users/{id}")
 *     public ApiResponse<UserResponse> getUser(@PathVariable Long id) {
 *         // ✅ Clean! No try-catch needed
 *         UserResponse user = userService.getUserById(id);
 *         return ApiResponse.success(user, "User retrieved successfully");
 *     }
 * }
 * 
 * // All exceptions automatically handled by GlobalExceptionHandler ✅
 * }
 * </pre>
 * 
 * How It Works:
 * 
 * 1. Exception occurs in service layer:
 *    - userService.createUser() throws ValidationException
 * 
 * 2. Exception propagates to controller:
 *    - Controller doesn't catch it, lets it bubble up
 * 
 * 3. Spring's DispatcherServlet catches exception:
 *    - Looks for @ExceptionHandler methods
 * 
 * 4. Matching handler found:
 *    - @ExceptionHandler(ValidationException.class)
 * 
 * 5. Handler executes:
 *    - Creates ErrorResponse
 *    - Returns ResponseEntity with appropriate HTTP status
 * 
 * 6. Client receives:
 *    - Consistent JSON error response
 * 
 * Flow Diagram:
 * 
 * <pre>
 * {@code
 * Client Request
 *     │
 *     ▼
 * Controller (No try-catch!)
 *     │
 *     ▼
 * Service Layer
 *     │
 *     ▼
 * throws ValidationException  ← Exception occurs
 *     │
 *     ▼
 * Spring DispatcherServlet intercepts
 *     │
 *     ▼
 * GlobalExceptionHandler.handleValidationException()
 *     │
 *     ├─► Creates ErrorResponse
 *     ├─► Logs error
 *     └─► Returns ResponseEntity<ErrorResponse>
 *     │
 *     ▼
 * Client receives JSON:
 * {
 *   "errorCode": "VALIDATION_ERROR",
 *   "message": "Email already exists",
 *   "status": 400,
 *   "timestamp": "2025-12-20T18:19:55"
 * }
 * }
 * </pre>
 * 
 * Exception Hierarchy:
 * 
 * <pre>
 * {@code
 * RuntimeException
 *     │
 *     └─► BaseException (common-lib)
 *           │
 *           ├─► ValidationException (HTTP 400)
 *           │     - Input validation failures
 *           │     - Business rule violations
 *           │     - Email already exists, etc.
 *           │
 *           ├─► ResourceNotFoundException (HTTP 404)
 *           │     - User not found
 *           │     - Entity doesn't exist
 *           │
 *           ├─► UnauthorizedException (HTTP 403)
 *           │     - Access denied
 *           │     - Insufficient permissions
 *           │
 *           └─► BusinessException (HTTP 422)
 *                 - Business logic errors
 *                 - Already premium, etc.
 * }
 * </pre>
 * 
 * Error Response Format:
 * 
 * All errors return consistent JSON structure:
 * <pre>
 * {@code
 * {
 *   "errorCode": "VALIDATION_ERROR",        // Machine-readable code
 *   "message": "User-friendly description", // Human-readable message
 *   "timestamp": "2025-12-20T18:19:55",    // When error occurred
 *   "status": 400,                          // HTTP status code
 *   "path": "/api/users",                   // Request path
 *   "details": {                            // Optional field-specific errors
 *     "email": "Email is required",
 *     "password": "Password too weak"
 *   }
 * }
 * }
 * </pre>
 * 
 * Logging Strategy:
 * 
 * 1. Client Errors (4xx):
 *    - Log at WARN level
 *    - Expected errors (invalid input, not found)
 *    - Don't clutter logs with stack traces
 * 
 * 2. Server Errors (5xx):
 *    - Log at ERROR level
 *    - Unexpected errors (bugs, infrastructure issues)
 *    - Include full stack trace for debugging
 * 
 * Security Considerations:
 * 
 * 1. Never expose sensitive information:
 *    - Database connection strings
 *    - Internal system details
 *    - Stack traces (in production)
 * 
 * 2. Sanitize error messages:
 *    - Generic messages for unexpected errors
 *    - Specific messages only for expected errors
 * 
 * 3. Rate limiting:
 *    - Too many errors from same IP → potential attack
 *    - Implement rate limiting at API gateway
 * 
 * Best Practices:
 * 
 * 1. Error Codes:
 *    - Use consistent naming (UPPER_SNAKE_CASE)
 *    - Document all error codes
 *    - Machine-readable for client handling
 * 
 * 2. Error Messages:
 *    - User-friendly language
 *    - Actionable (tell user how to fix)
 *    - Avoid technical jargon
 * 
 * 3. HTTP Status Codes:
 *    - Use correct status codes
 *    - 400: Client error (bad input)
 *    - 404: Resource not found
 *    - 422: Unprocessable entity (business logic)
 *    - 500: Server error (bugs)
 * 
 * Testing:
 * 
 * Unit Tests:
 * - Test each exception handler separately
 * - Verify correct HTTP status
 * - Verify error response structure
 * 
 * Integration Tests:
 * - Trigger real exceptions from endpoints
 * - Verify end-to-end error handling
 * - Test error response format
 * 
 * @see ErrorResponse
 * @see BaseException
 * @see ValidationException
 * @see ResourceNotFoundException
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles ValidationException (HTTP 400 Bad Request).
     * 
     * Triggered when:
     * - Input validation fails (email format, password strength)
     * - Business validation fails (email already exists)
     * - Required fields missing
     * 
     * Example Scenarios:
     * - Email already registered
     * - Password too weak
     * - Invalid phone number format
     * - Premium requirements not met
     * 
     * Response Example:
     * <pre>
     * {@code
     * HTTP 400 Bad Request
     * {
     *   "errorCode": "EMAIL_ALREADY_EXISTS",
     *   "message": "Email address is already registered: john@example.com",
     *   "timestamp": "2025-12-20T18:19:55",
     *   "status": 400,
     *   "path": "/api/users"
     * }
     * }
     * </pre>
     * 
     * @param ex ValidationException thrown by service
     * @param request WebRequest for extracting request path
     * @return ResponseEntity with ErrorResponse and HTTP 400
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        ValidationException ex, 
        WebRequest request
    ) {
        log.warn("Validation error: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode(ex.getErrorCode())
            .message(ex.getMessage())
            .timestamp(ex.getTimestamp())
            .status(ex.getHttpStatus().value())
            .path(extractPath(request))
            .build();
        
        return new ResponseEntity<>(errorResponse, ex.getHttpStatus());
    }

    /**
     * Handles ResourceNotFoundException (HTTP 404 Not Found).
     * 
     * Triggered when:
     * - User not found by ID
     * - User not found by email
     * - Any requested resource doesn't exist
     * 
     * Example Scenarios:
     * - GET /api/users/999 (user doesn't exist)
     * - GET /api/users/email/nonexistent@example.com
     * 
     * Response Example:
     * <pre>
     * {@code
     * HTTP 404 Not Found
     * {
     *   "errorCode": "RESOURCE_NOT_FOUND",
     *   "message": "User not found with id: '999'",
     *   "timestamp": "2025-12-20T18:19:55",
     *   "status": 404,
     *   "path": "/api/users/999"
     * }
     * }
     * </pre>
     * 
     * @param ex ResourceNotFoundException thrown by service
     * @param request WebRequest for extracting request path
     * @return ResponseEntity with ErrorResponse and HTTP 404
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
        ResourceNotFoundException ex,
        WebRequest request
    ) {
        log.warn("Resource not found: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode(ex.getErrorCode())
            .message(ex.getMessage())
            .timestamp(ex.getTimestamp())
            .status(ex.getHttpStatus().value())
            .path(extractPath(request))
            .build();
        
        return new ResponseEntity<>(errorResponse, ex.getHttpStatus());
    }

    /**
     * Handles BusinessException (HTTP 422 Unprocessable Entity or custom status).
     * 
     * Triggered when:
     * - Business rule violations
     * - State transitions not allowed
     * - Operations invalid for current state
     * 
     * Example Scenarios:
     * - User already premium (can't upgrade again)
     * - Account inactive (can't perform action)
     * - Insufficient permissions
     * 
     * Response Example:
     * <pre>
     * {@code
     * HTTP 409 Conflict
     * {
     *   "errorCode": "USER_ALREADY_PREMIUM",
     *   "message": "User is already a PREMIUM member",
     *   "timestamp": "2025-12-20T18:19:55",
     *   "status": 409,
     *   "path": "/api/users/1/upgrade"
     * }
     * }
     * </pre>
     * 
     * @param ex BusinessException thrown by service
     * @param request WebRequest for extracting request path
     * @return ResponseEntity with ErrorResponse and custom HTTP status
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
        BusinessException ex,
        WebRequest request
    ) {
        log.warn("Business exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode(ex.getErrorCode())
            .message(ex.getMessage())
            .timestamp(ex.getTimestamp())
            .status(ex.getHttpStatus().value())
            .path(extractPath(request))
            .build();
        
        return new ResponseEntity<>(errorResponse, ex.getHttpStatus());
    }

    /**
     * Handles MethodArgumentNotValidException (HTTP 400 Bad Request).
     * 
     * Triggered when:
     * - @Valid annotation fails on request body
     * - Bean Validation (@NotNull, @Email, @Size) fails
     * 
     * This provides field-level error details for input validation.
     * 
     * Example Scenario:
     * <pre>
     * {@code
     * POST /api/users
     * {
     *   "email": "invalid-email",      // ❌ Not valid email format
     *   "password": "weak",             // ❌ Too short (min 8)
     *   "name": ""                      // ❌ Blank (required)
     * }
     * }
     * </pre>
     * 
     * Response Example:
     * <pre>
     * {@code
     * HTTP 400 Bad Request
     * {
     *   "errorCode": "VALIDATION_FAILED",
     *   "message": "Validation failed for request body",
     *   "timestamp": "2025-12-20T18:19:55",
     *   "status": 400,
     *   "path": "/api/users",
     *   "details": {
     *     "email": "Email must be valid format",
     *     "password": "Password must be between 8 and 100 characters",
     *     "name": "Name is required"
     *   }
     * }
     * }
     * </pre>
     * 
     * @param ex MethodArgumentNotValidException from @Valid
     * @param request WebRequest for extracting request path
     * @return ResponseEntity with ErrorResponse and field errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        WebRequest request
    ) {
        log.warn("Validation failed for request body: {} field errors", 
            ex.getBindingResult().getFieldErrorCount());
        
        // Extract field errors into map
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
            log.debug("Field error: {} - {}", error.getField(), error.getDefaultMessage());
        }
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode("VALIDATION_FAILED")
            .message("Validation failed for request body")
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .path(extractPath(request))
            .details(fieldErrors)  // Field-specific errors
            .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles all other exceptions (HTTP 500 Internal Server Error).
     * 
     * This is the catch-all handler for unexpected exceptions:
     * - NullPointerException (bug in code)
     * - Database connection errors
     * - OutOfMemoryError
     * - Any unhandled RuntimeException
     * 
     * Important:
     * - Log full stack trace (ERROR level)
     * - Return generic message (don't expose internals)
     * - Alert monitoring system (production)
     * 
     * Example Response:
     * <pre>
     * {@code
     * HTTP 500 Internal Server Error
     * {
     *   "errorCode": "INTERNAL_SERVER_ERROR",
     *   "message": "An unexpected error occurred. Please try again later.",
     *   "timestamp": "2025-12-20T18:19:55",
     *   "status": 500,
     *   "path": "/api/users"
     * }
     * }
     * </pre>
     * 
     * Security Note:
     * - Never expose stack traces to clients (security risk)
     * - Log full details internally for debugging
     * - Generic message: "An unexpected error occurred"
     * 
     * @param ex Any unhandled exception
     * @param request WebRequest for extracting request path
     * @return ResponseEntity with generic ErrorResponse and HTTP 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
        Exception ex,
        WebRequest request
    ) {
        // Log full stack trace for debugging (ERROR level)
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        
        // Return generic message (don't expose internals)
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode("INTERNAL_SERVER_ERROR")
            .message("An unexpected error occurred. Please try again later.")
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .path(extractPath(request))
            .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Extracts request path from WebRequest.
     * 
     * Used to populate the "path" field in ErrorResponse.
     * Helps identify which endpoint caused the error.
     * 
     * Example:
     * - Request: POST /api/users
     * - Path: /api/users
     * 
     * @param request WebRequest
     * @return Request path (e.g., "/api/users")
     */
    private String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}

