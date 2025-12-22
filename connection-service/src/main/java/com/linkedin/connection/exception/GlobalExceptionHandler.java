package com.linkedin.connection.exception;

import com.linkedin.common.dto.ErrorResponse;
import com.linkedin.common.exceptions.BaseException;
import com.linkedin.common.exceptions.BusinessException;
import com.linkedin.common.exceptions.ResourceNotFoundException;
import com.linkedin.common.exceptions.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for the Connection Service.
 * Catches all exceptions thrown by controllers and converts them into
 * standardized error responses.
 * 
 * Design Pattern: Aspect-Oriented Programming (AOP)
 * - @RestControllerAdvice acts as an aspect that intercepts exceptions
 * - Provides centralized exception handling across all controllers
 * - Ensures consistent error response format
 * 
 * Exception Hierarchy:
 * - BaseException (from common-lib)
 *   ├─ ResourceNotFoundException (404)
 *   ├─ ValidationException (400)
 *   ├─ BusinessException (400)
 *   └─ UnauthorizedException (401)
 * 
 * Benefits:
 * 1. **Consistency**: All errors follow the same format
 * 2. **Separation of Concerns**: Controllers don't handle exceptions
 * 3. **Security**: No stack traces exposed to clients
 * 4. **Maintainability**: Single place to update error handling
 * 
 * Error Response Format:
 * {
 *   "errorCode": "RESOURCE_NOT_FOUND",
 *   "message": "Connection not found",
 *   "timestamp": "2025-12-21T10:30:00",
 *   "status": 404,
 *   "path": "/api/connections/123",
 *   "details": { ... } // Optional
 * }
 * 
 * @see ErrorResponse
 * @see BaseException
 * @see com.linkedin.connection.controller.ConnectionController
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // =========================================================================
    // Custom Exceptions from common-lib
    // =========================================================================

    /**
     * Handles ResourceNotFoundException (404).
     * Thrown when a requested resource (connection, user, etc.) is not found.
     * 
     * Example: GET /api/connections/999 where connection 999 doesn't exist
     * 
     * @param ex The exception thrown
     * @param request The web request
     * @return ResponseEntity with 404 status and error details
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            WebRequest request) {
        
        log.warn("Resource not found: {} - Path: {}", ex.getMessage(), request.getDescription(false));
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(ex.getTimestamp())
                .status(HttpStatus.NOT_FOUND.value())
                .path(extractPath(request))
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles ValidationException (400).
     * Thrown when business rule validation fails.
     * 
     * Examples:
     * - Cannot connect to yourself
     * - Duplicate connection request
     * - Cannot accept connection in REJECTED state
     * 
     * @param ex The exception thrown
     * @param request The web request
     * @return ResponseEntity with 400 status and error details
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex,
            WebRequest request) {
        
        log.warn("Validation error: {} - Path: {}", ex.getMessage(), request.getDescription(false));
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(ex.getTimestamp())
                .status(HttpStatus.BAD_REQUEST.value())
                .path(extractPath(request))
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles BusinessException (400).
     * Thrown when general business logic errors occur.
     * 
     * @param ex The exception thrown
     * @param request The web request
     * @return ResponseEntity with 400 status and error details
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            WebRequest request) {
        
        log.warn("Business logic error: {} - Path: {}", ex.getMessage(), request.getDescription(false));
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(ex.getTimestamp())
                .status(HttpStatus.BAD_REQUEST.value())
                .path(extractPath(request))
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles BaseException (generic custom exceptions).
     * Catches any custom exception that extends BaseException but isn't
     * handled by more specific handlers above.
     * 
     * @param ex The exception thrown
     * @param request The web request
     * @return ResponseEntity with status from exception and error details
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(
            BaseException ex,
            WebRequest request) {
        
        log.warn("Custom exception: {} - Path: {}", ex.getMessage(), request.getDescription(false));
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(ex.getTimestamp())
                .status(ex.getHttpStatus().value())
                .path(extractPath(request))
                .build();
        
        return new ResponseEntity<>(errorResponse, ex.getHttpStatus());
    }

    // =========================================================================
    // Bean Validation Exceptions
    // =========================================================================

    /**
     * Handles MethodArgumentNotValidException (400).
     * Thrown when @Valid validation fails on request DTOs.
     * 
     * Example: POST /api/connections/requests with missing addresseeId
     * 
     * Bean Validation Annotations:
     * - @NotNull, @NotBlank, @Email, @Size, @Positive, etc.
     * 
     * Response includes all validation errors in the "details" map.
     * 
     * @param ex The exception thrown
     * @param request The web request
     * @return ResponseEntity with 400 status and validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        
        log.warn("Validation failed for request to {}: {} errors", 
                request.getDescription(false), ex.getBindingResult().getErrorCount());
        
        // Extract all field validation errors
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
            log.debug("  - Field '{}': {}", fieldName, errorMessage);
        });
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message("Request validation failed. Please check the provided data.")
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .path(extractPath(request))
                .details(validationErrors)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // =========================================================================
    // Spring Security Exceptions
    // =========================================================================

    /**
     * Handles AccessDeniedException (403).
     * Thrown when a user tries to access a resource they don't have permission for.
     * 
     * Example: User A trying to accept a connection request sent to User B
     * 
     * @param ex The exception thrown
     * @param request The web request
     * @return ResponseEntity with 403 status and error details
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request) {
        
        log.warn("Access denied: {} - Path: {}", ex.getMessage(), request.getDescription(false));
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("ACCESS_DENIED")
                .message("You do not have permission to access this resource.")
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .path(extractPath(request))
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // State Transition Exceptions
    // =========================================================================

    /**
     * Handles UnsupportedOperationException (400).
     * Thrown by state handlers when an invalid state transition is attempted.
     * 
     * Examples:
     * - Trying to accept an already accepted connection
     * - Trying to remove a PENDING connection
     * - Trying to reject a REJECTED connection
     * 
     * @param ex The exception thrown
     * @param request The web request
     * @return ResponseEntity with 400 status and error details
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedOperationException(
            UnsupportedOperationException ex,
            WebRequest request) {
        
        log.warn("Unsupported operation: {} - Path: {}", ex.getMessage(), request.getDescription(false));
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INVALID_STATE_TRANSITION")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .path(extractPath(request))
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles IllegalStateException (400).
     * Thrown when an operation is attempted on an object in an invalid state.
     * 
     * Example: Trying to accept a connection that's not in PENDING state
     * 
     * @param ex The exception thrown
     * @param request The web request
     * @return ResponseEntity with 400 status and error details
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            WebRequest request) {
        
        log.warn("Illegal state: {} - Path: {}", ex.getMessage(), request.getDescription(false));
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("ILLEGAL_STATE")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .path(extractPath(request))
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles IllegalArgumentException (400).
     * Thrown when an invalid argument is passed to a method.
     * 
     * Example: Null values where non-null is expected
     * 
     * @param ex The exception thrown
     * @param request The web request
     * @return ResponseEntity with 400 status and error details
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {
        
        log.warn("Illegal argument: {} - Path: {}", ex.getMessage(), request.getDescription(false));
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INVALID_ARGUMENT")
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .path(extractPath(request))
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // =========================================================================
    // Generic Exception Handler (Catch-All)
    // =========================================================================

    /**
     * Handles all other exceptions not caught by specific handlers (500).
     * This is the catch-all handler for unexpected errors.
     * 
     * Important:
     * - Logs full stack trace for debugging
     * - Does NOT expose stack trace to client (security)
     * - Returns generic error message
     * 
     * @param ex The exception thrown
     * @param request The web request
     * @return ResponseEntity with 500 status and generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            WebRequest request) {
        
        // Log full exception with stack trace for debugging
        log.error("Unexpected error occurred - Path: {}", request.getDescription(false), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Please try again later or contact support.")
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .path(extractPath(request))
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Extracts the request path from WebRequest.
     * Removes the "uri=" prefix that Spring adds.
     * 
     * @param request The web request
     * @return The clean request path
     */
    private String extractPath(WebRequest request) {
        String description = request.getDescription(false);
        return description.replace("uri=", "");
    }
}

