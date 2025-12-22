package com.linkedin.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkedin.common.dto.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * JWT Authentication Entry Point.
 * 
 * This component handles authentication failures and unauthorized access attempts.
 * It is invoked by Spring Security when a user tries to access a protected resource
 * without proper authentication (missing, invalid, or expired JWT token).
 *
 * Instead of redirecting to a login page (traditional web app behavior), this returns
 * a JSON error response with HTTP 401 Unauthorized status, which is appropriate for
 * RESTful APIs.
 *
 * Flow:
 * 1. User attempts to access a protected endpoint without a valid JWT token
 * 2. {@link JwtAuthenticationFilter} fails to authenticate (or skips authentication)
 * 3. Spring Security's filter chain detects the lack of authentication
 * 4. This entry point is triggered to handle the error
 * 5. A structured JSON error response is returned to the client
 *
 * Design Considerations:
 * - **Consistent Error Format**: Uses {@link ErrorResponse} for a uniform API contract.
 * - **Logging**: Logs all unauthorized access attempts for security monitoring.
 * - **Stateless**: Does not store or redirect; simply returns an error response.
 * - **Client-Friendly**: Provides clear error messages to help API consumers understand the issue.
 *
 * Example Response:
 * <pre>
 * HTTP/1.1 401 Unauthorized
 * Content-Type: application/json
 * 
 * {
 *   "errorCode": "UNAUTHORIZED_ACCESS",
 *   "message": "Full authentication is required to access this resource. Please provide a valid JWT token.",
 *   "timestamp": "2025-12-20T15:30:00",
 *   "status": 401,
 *   "path": "/api/users/123"
 * }
 * </pre>
 *
 * @see JwtAuthenticationFilter
 * @see SecurityConfig
 * @see ErrorResponse
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * Handles unauthorized access attempts.
     * This method is called whenever an {@link AuthenticationException} is thrown
     * because a user is trying to access a protected resource without authentication.
     *
     * @param request       The HTTP request that resulted in an {@link AuthenticationException}.
     * @param response      The HTTP response to send to the client.
     * @param authException The exception that was thrown (provides details about the failure).
     * @throws IOException      if an I/O error occurs while writing the response.
     * @throws ServletException if a servlet error occurs.
     */
    @Override
    public void commence(HttpServletRequest request,
                        HttpServletResponse response,
                        AuthenticationException authException) throws IOException, ServletException {

        // Log the unauthorized access attempt
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = getClientIp(request);
        
        log.warn("Unauthorized access attempt: {} {} from IP: {}. Reason: {}",
            method, requestPath, clientIp, authException.getMessage());

        // Build structured error response
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode("UNAUTHORIZED_ACCESS")
            .message("Full authentication is required to access this resource. Please provide a valid JWT token.")
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.UNAUTHORIZED.value())
            .path(requestPath)
            .build();

        // Set response properties
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setCharacterEncoding("UTF-8");

        // Write JSON error response
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        response.getWriter().flush();

        log.debug("Sent 401 Unauthorized response for request to: {}", requestPath);
    }

    /**
     * Extracts the client's IP address from the request.
     * Considers X-Forwarded-For header for cases where the app is behind a proxy/load balancer.
     *
     * @param request The HTTP request.
     * @return The client's IP address.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs; the first one is the original client
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

