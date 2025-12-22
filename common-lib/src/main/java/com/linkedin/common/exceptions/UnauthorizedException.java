package com.linkedin.common.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a user lacks permission to perform an action.
 * 
 * Use this exception when:
 * - User tries to modify/delete resources they don't own
 * - User tries to access resources they don't have permission for
 * - User role doesn't have required privileges (e.g., admin-only action)
 * 
 * Important: This is different from authentication (401).
 * - 401 Unauthorized: User is not authenticated (invalid/missing token)
 * - 403 Forbidden: User is authenticated but lacks permission
 * 
 * Always returns HTTP 403 (Forbidden) to the client.
 */
public class UnauthorizedException extends BaseException {

    /**
     * Creates an unauthorized exception with a custom message
     * 
     * @param message Error message explaining what permission is lacking
     */
    public UnauthorizedException(String message) {
        super(message, "UNAUTHORIZED_ACCESS", HttpStatus.FORBIDDEN);
    }

    /**
     * Creates an unauthorized exception with custom message and error code
     * 
     * @param message   Error message explaining what permission is lacking
     * @param errorCode Specific error code for tracking (e.g., "POST_DELETE_403")
     */
    public UnauthorizedException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.FORBIDDEN);
    }
}

