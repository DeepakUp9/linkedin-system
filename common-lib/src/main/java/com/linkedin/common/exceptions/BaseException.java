package com.linkedin.common.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * Base exception class for all custom exceptions in the LinkedIn system.
 * Provides common fields: error code, HTTP status, and timestamp.
 * 
 * All custom exceptions should extend this class to ensure consistent
 * error handling across all microservices.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    /**
     * Unique error code for identifying the specific error type
     * Example: "USER_001", "POST_404", "CONN_403"
     */
    private final String errorCode;

    /**
     * HTTP status code to be returned to the client
     * Example: 400 (Bad Request), 404 (Not Found), 500 (Internal Server Error)
     */
    private final HttpStatus httpStatus;

    /**
     * Timestamp when the exception was created
     */
    private final LocalDateTime timestamp;

    /**
     * Constructor with message, error code, and HTTP status
     *
     * @param message    Human-readable error message
     * @param errorCode  Unique error code for this exception type
     * @param httpStatus HTTP status to return to client
     */
    protected BaseException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructor with message, cause, error code, and HTTP status
     * Used when wrapping another exception
     *
     * @param message    Human-readable error message
     * @param cause      The underlying exception that caused this error
     * @param errorCode  Unique error code for this exception type
     * @param httpStatus HTTP status to return to client
     */
    protected BaseException(String message, Throwable cause, String errorCode, HttpStatus httpStatus) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.timestamp = LocalDateTime.now();
    }
}

