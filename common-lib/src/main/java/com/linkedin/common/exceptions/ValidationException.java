package com.linkedin.common.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when input validation fails.
 * 
 * Use this exception for:
 * - Invalid email/phone format
 * - Missing required fields
 * - Value out of range
 * - Business rule validation failures
 * 
 * Always returns HTTP 400 (Bad Request) to the client.
 */
public class ValidationException extends BaseException {

    /**
     * Creates a validation exception with a custom error code
     *
     * @param message   Human-readable error message (e.g., "Email format is invalid")
     * @param errorCode Unique error code (e.g., "USER_VAL_001", "POST_VAL_002")
     */
    public ValidationException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a validation exception with a default error code
     * Use this for generic validation errors
     *
     * @param message Human-readable error message
     */
    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a validation exception by wrapping another exception
     * Useful when validation logic throws an exception (e.g., parsing error)
     *
     * @param message   Human-readable error message
     * @param cause     The underlying exception that caused this validation failure
     * @param errorCode Unique error code
     */
    public ValidationException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode, HttpStatus.BAD_REQUEST);
    }
}

