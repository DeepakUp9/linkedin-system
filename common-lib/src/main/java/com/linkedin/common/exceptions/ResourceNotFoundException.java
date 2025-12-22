package com.linkedin.common.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested resource is not found in the database.
 * 
 * Use this exception when:
 * - User/Post/Job/Company with given ID doesn't exist
 * - Connection request is not found
 * - Search returns no results for a specific identifier
 * 
 * Always returns HTTP 404 (Not Found) to the client.
 */
public class ResourceNotFoundException extends BaseException {

    /**
     * Creates a resource not found exception with detailed information
     * 
     * @param resourceName Name of the resource (e.g., "User", "Post", "Connection")
     * @param fieldName    Field used to search (e.g., "id", "email", "username")
     * @param fieldValue   Value that was searched for (e.g., "123", "john@example.com")
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(
            String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue),
            "RESOURCE_NOT_FOUND",
            HttpStatus.NOT_FOUND
        );
    }

    /**
     * Creates a resource not found exception with a custom message
     * 
     * @param message Custom error message
     */
    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    /**
     * Creates a resource not found exception with custom message and error code
     * 
     * @param message   Custom error message
     * @param errorCode Specific error code for tracking
     */
    public ResourceNotFoundException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.NOT_FOUND);
    }
}

