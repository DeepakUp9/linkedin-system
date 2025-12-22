package com.linkedin.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response structure returned to clients for all exceptions.
 * 
 * This DTO ensures consistent error formatting across all microservices,
 * making it easier for frontend applications to handle errors uniformly.
 * 
 * Example JSON:
 * {
 *   "errorCode": "USER_NOT_FOUND",
 *   "message": "User not found with id: '123'",
 *   "timestamp": "2025-12-20T10:45:00",
 *   "status": 404,
 *   "path": "/api/users/123",
 *   "details": { "field": "userId", "value": "123" }
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // Exclude null fields from JSON
public class ErrorResponse {

    /**
     * Unique error code for identifying the error type
     * Examples: "USER_NOT_FOUND", "VALIDATION_ERROR", "CONN_ALREADY_EXISTS"
     */
    private String errorCode;

    /**
     * Human-readable error message
     * Should be clear and actionable for the client
     */
    private String message;

    /**
     * When the error occurred
     * Format: ISO-8601 (2025-12-20T10:45:00)
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * HTTP status code
     * Examples: 400 (Bad Request), 404 (Not Found), 500 (Internal Server Error)
     */
    private int status;

    /**
     * The request path that caused the error
     * Example: "/api/users/123"
     */
    private String path;

    /**
     * Optional additional details about the error
     * Useful for validation errors with multiple field-level errors
     * 
     * Example for validation error:
     * {
     *   "email": "Invalid email format",
     *   "age": "Must be at least 18"
     * }
     */
    private Map<String, String> details;
}

