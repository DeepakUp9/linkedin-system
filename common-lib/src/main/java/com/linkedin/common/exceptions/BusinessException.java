package com.linkedin.common.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a business rule is violated.
 * 
 * Use this exception when:
 * - Action violates business logic (not validation, auth, or not-found)
 * - Resource is in wrong state for the requested operation
 * - Business constraints are not met (e.g., limits exceeded, deadlines passed)
 * - Conflicts with existing data
 * 
 * Unlike other exceptions, this allows flexible HTTP status codes
 * since business rules can result in different error types:
 * - 409 Conflict: Resource already exists
 * - 422 Unprocessable Entity: Request valid but cannot be processed
 * - 400 Bad Request: General business rule violation
 * - 423 Locked: Resource is locked
 */
public class BusinessException extends BaseException {

    /**
     * Creates a business exception with custom status code
     * 
     * @param message    Error message explaining the business rule violation
     * @param httpStatus HTTP status code appropriate for this violation
     */
    public BusinessException(String message, HttpStatus httpStatus) {
        super(message, "BUSINESS_RULE_VIOLATION", httpStatus);
    }

    /**
     * Creates a business exception with custom status and error code
     * 
     * @param message    Error message explaining the business rule violation
     * @param errorCode  Specific error code for tracking
     * @param httpStatus HTTP status code appropriate for this violation
     */
    public BusinessException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus);
    }

    /**
     * Creates a business exception with default 422 status (Unprocessable Entity)
     * Use this when the request is valid but cannot be processed due to business logic
     * 
     * @param message Error message explaining why the request cannot be processed
     */
    public BusinessException(String message) {
        super(message, "BUSINESS_RULE_VIOLATION", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Creates a business exception by wrapping another exception
     * 
     * @param message    Error message explaining the business rule violation
     * @param cause      The underlying exception that caused this business error
     * @param errorCode  Specific error code for tracking
     * @param httpStatus HTTP status code appropriate for this violation
     */
    public BusinessException(String message, Throwable cause, String errorCode, HttpStatus httpStatus) {
        super(message, cause, errorCode, httpStatus);
    }
}

