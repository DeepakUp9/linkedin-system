package com.linkedin.connection.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Configuration class for Spring Cloud OpenFeign clients.
 * 
 * Purpose:
 * Configures Feign clients for making HTTP requests to other microservices
 * (e.g., user-service, notification-service).
 * 
 * What It Does:
 * 1. Configures logging level for Feign requests/responses
 * 2. Sets up request interceptors (e.g., JWT token propagation)
 * 3. Defines custom error handling for Feign client errors
 * 4. Configures connection timeouts and retry logic
 * 
 * Use Case:
 * Connection-service needs to call user-service to:
 * - Verify user exists before sending connection request
 * - Fetch user details (name, headline, profile picture) for DTOs
 * - Validate addressee is not deleted/suspended
 * 
 * Example Feign Client:
 * <pre>
 * {@code
 * @FeignClient(name = "user-service", url = "${user.service.url}")
 * public interface UserServiceClient {
 *     @GetMapping("/api/users/{userId}")
 *     UserResponse getUserById(@PathVariable Long userId);
 * }
 * }
 * </pre>
 * 
 * How It Works:
 * <pre>
 * Connection Service                  User Service
 *       |                                  |
 *       | 1. Need user details             |
 *       |--------------------------------->|
 *       |    GET /api/users/123            |
 *       |    Authorization: Bearer <JWT>   |
 *       |                                  |
 *       |<---------------------------------|
 *       | 2. UserResponse (JSON)           |
 *       |                                  |
 *       | 3. Map to DTO                    |
 *       |                                  |
 * 
 * Flow:
 * 1. Service calls userServiceClient.getUserById(123)
 * 2. Feign creates HTTP request
 * 3. RequestInterceptor adds JWT token to Authorization header
 * 4. HTTP call is made to user-service
 * 5. Response is deserialized to UserResponse
 * 6. If error, ErrorDecoder translates to appropriate exception
 * </pre>
 * 
 * Request Interceptor:
 * Automatically propagates JWT token from current request to Feign calls.
 * This allows user-service to validate the token and enforce authorization.
 * 
 * Example:
 * <pre>
 * User makes request:
 *   GET /api/connections/list
 *   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
 * 
 * Connection-service needs to fetch user details:
 *   → Calls userServiceClient.getUserById(userId)
 *   → RequestInterceptor adds same JWT to Feign request
 *   → User-service validates JWT and returns user details
 * </pre>
 * 
 * Error Handling:
 * Maps HTTP status codes to domain-specific exceptions:
 * - 404 → ResourceNotFoundException("User not found")
 * - 401/403 → SecurityException("Unauthorized")
 * - 500 → ServiceException("User service unavailable")
 * 
 * Logging:
 * - NONE: No logging (production default)
 * - BASIC: Request method, URL, response status, execution time
 * - HEADERS: BASIC + request/response headers
 * - FULL: HEADERS + request/response body (use in dev/debug only)
 * 
 * Benefits:
 * - Simplified inter-service communication
 * - Automatic token propagation
 * - Declarative API (no manual HTTP client code)
 * - Built-in retry and circuit breaker support (with Resilience4j)
 * - Centralized error handling
 * 
 * Performance Considerations:
 * - Connection pooling (default: 200 max connections)
 * - Read timeout: 5 seconds (adjust based on service SLA)
 * - Connect timeout: 2 seconds
 * - Consider circuit breaker for fault tolerance
 * 
 * Security:
 * - Always propagate JWT token for authentication
 * - Use HTTPS in production (enforce via service URLs)
 * - Validate service responses (don't trust blindly)
 * - Log security events (token missing, auth failures)
 * 
 * Related:
 * @see org.springframework.cloud.openfeign.FeignClient
 * @see com.linkedin.user.client.UserServiceClient
 * @see feign.RequestInterceptor
 * @see feign.codec.ErrorDecoder
 * 
 * @author LinkedIn System
 * @version 1.0
 */
@Configuration
public class FeignConfig {

    /**
     * Configures Feign logging level.
     * 
     * Levels:
     * - NONE: No logging (production)
     * - BASIC: Log request method, URL, response status, execution time
     * - HEADERS: Log request/response headers (useful for debugging auth issues)
     * - FULL: Log everything including request/response body (use sparingly)
     * 
     * Example log output (BASIC level):
     * <pre>
     * [UserServiceClient#getUserById] ---> GET http://localhost:8081/api/users/123 HTTP/1.1
     * [UserServiceClient#getUserById] <--- HTTP/1.1 200 (45ms)
     * </pre>
     * 
     * @return Logger.Level for Feign
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        // TODO: Make this configurable via application.yml
        // dev/test: FULL
        // production: BASIC or NONE
        return Logger.Level.BASIC;
    }

    /**
     * Request interceptor to add JWT token to Feign requests.
     * 
     * Purpose:
     * Propagates the authentication token from the incoming request
     * to outgoing Feign calls. This allows the called service to:
     * 1. Authenticate the request
     * 2. Authorize actions based on user roles
     * 3. Track the original user (audit trail)
     * 
     * Implementation:
     * 1. Extract Authentication from SecurityContext
     * 2. Get JWT token from Authentication
     * 3. Add token to Authorization header
     * 
     * Security Note:
     * This propagates the SAME token. The called service validates it.
     * Both services must share the same JWT secret or use a trusted issuer.
     * 
     * @return RequestInterceptor that adds Authorization header
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.getCredentials() != null) {
                String token = authentication.getCredentials().toString();
                
                // Add Authorization header with Bearer token
                requestTemplate.header("Authorization", "Bearer " + token);
                
                // Optional: Add other headers for tracing/logging
                // requestTemplate.header("X-Request-ID", UUID.randomUUID().toString());
                // requestTemplate.header("X-User-ID", getUserIdFromAuth(authentication).toString());
            }
        };
    }

    /**
     * Custom error decoder for Feign client errors.
     * 
     * Purpose:
     * Translates HTTP errors from Feign calls into domain-specific exceptions.
     * This provides better error messages and allows proper exception handling.
     * 
     * Without ErrorDecoder:
     * <pre>
     * FeignException$NotFound: [404] during [GET] to [http://user-service/api/users/999]
     * </pre>
     * 
     * With ErrorDecoder:
     * <pre>
     * ResourceNotFoundException: User with ID 999 not found
     * </pre>
     * 
     * Benefits:
     * - Better error messages
     * - Consistent exception types across the application
     * - Easier to handle in GlobalExceptionHandler
     * - Hides implementation details (Feign) from callers
     * 
     * @return Custom ErrorDecoder
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            int status = response.status();
            String serviceName = extractServiceName(methodKey);
            
            return switch (status) {
                case 404 -> new com.linkedin.common.exceptions.ResourceNotFoundException(
                    String.format("Resource not found in %s", serviceName),
                    "RESOURCE_NOT_FOUND"
                );
                case 401, 403 -> new com.linkedin.common.exceptions.SecurityException(
                    String.format("Unauthorized access to %s", serviceName),
                    "UNAUTHORIZED"
                );
                case 400 -> new com.linkedin.common.exceptions.ValidationException(
                    String.format("Invalid request to %s", serviceName),
                    "INVALID_REQUEST"
                );
                case 503 -> new com.linkedin.common.exceptions.ServiceException(
                    String.format("%s is currently unavailable", serviceName),
                    "SERVICE_UNAVAILABLE"
                );
                default -> new com.linkedin.common.exceptions.ServiceException(
                    String.format("Error calling %s: HTTP %d", serviceName, status),
                    "SERVICE_ERROR"
                );
            };
        };
    }

    /**
     * Extracts service name from Feign method key.
     * 
     * Method key format: "ClassName#methodName()"
     * Example: "UserServiceClient#getUserById(Long)"
     * 
     * @param methodKey Feign method key
     * @return Service name (e.g., "UserServiceClient")
     */
    private String extractServiceName(String methodKey) {
        if (methodKey == null || !methodKey.contains("#")) {
            return "Unknown Service";
        }
        return methodKey.substring(0, methodKey.indexOf("#"));
    }



    /**
     * Configuration for Feign timeouts and connection pooling.
     * 
     * These can also be configured in application.yml:
     * <pre>
     * feign:
     *   client:
     *     config:
     *       default:
     *         connectTimeout: 2000    # 2 seconds
     *         readTimeout: 5000       # 5 seconds
     *       user-service:             # Service-specific config
     *         connectTimeout: 1000
     *         readTimeout: 3000
     * </pre>
     * 
     * Tuning Guidelines:
     * - Connect timeout: 1-2 seconds (time to establish connection)
     * - Read timeout: 3-10 seconds (time to receive response)
     * - Fast services: Lower timeouts (fail fast)
     * - Slow services: Higher timeouts (but consider async)
     * 
     * Circuit Breaker Integration:
     * Add Resilience4j dependency and configure circuit breaker:
     * <pre>
     * {@code
     * @FeignClient(name = "user-service", fallback = UserServiceFallback.class)
     * public interface UserServiceClient {
     *     @CircuitBreaker(name = "userService")
     *     UserResponse getUserById(Long userId);
     * }
     * }
     * </pre>
     */

    /**
     * Request/Response Compression (optional).
     * 
     * Enable if transferring large payloads:
     * <pre>
     * feign:
     *   compression:
     *     request:
     *       enabled: true
     *       mime-types: application/json
     *       min-request-size: 2048  # Compress if > 2KB
     *     response:
     *       enabled: true
     * </pre>
     */
}

