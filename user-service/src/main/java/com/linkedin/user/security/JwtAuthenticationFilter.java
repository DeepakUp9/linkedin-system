package com.linkedin.user.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter.
 * This filter intercepts every HTTP request to extract and validate JWT tokens.
 * If a valid token is found, it sets up Spring Security's authentication context.
 *
 * Flow:
 * 1. Extract JWT token from "Authorization: Bearer <token>" header
 * 2. Validate token using {@link JwtTokenProvider}
 * 3. Extract user email from token
 * 4. Load user details using {@link CustomUserDetailsService}
 * 5. Create authentication object and set in {@link SecurityContextHolder}
 *
 * This filter extends {@link OncePerRequestFilter} to ensure it's executed once per request,
 * even if the request is forwarded or included multiple times internally.
 *
 * Design Considerations:
 * - **Stateless Authentication**: Does not rely on server-side sessions; all user info
 *   is extracted from the JWT token itself.
 * - **Security Context**: Sets up Spring Security's authentication context so that
 *   subsequent filters and controllers can access the authenticated user via
 *   {@link SecurityContextHolder} or {@code @AuthenticationPrincipal}.
 * - **Error Handling**: Catches JWT validation exceptions and logs them, but allows
 *   the request to proceed (without authentication). Spring Security's
 *   {@code AuthenticationEntryPoint} will handle unauthorized access attempts.
 * - **Performance**: Only validates tokens if present; skips processing for public endpoints.
 *
 * @see JwtTokenProvider
 * @see CustomUserDetailsService
 * @see SecurityContextHolder
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    /**
     * The main filter logic executed for each request.
     *
     * @param request     The HTTP request.
     * @param response    The HTTP response.
     * @param filterChain The filter chain to continue processing the request.
     * @throws ServletException if a servlet error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            // 1. Extract JWT token from the request header
            String jwt = extractJwtFromRequest(request);

            // 2. If token is present and valid, authenticate the user
            if (StringUtils.hasText(jwt)) {
                authenticateUser(jwt, request);
            } else {
                log.debug("No JWT token found in request to: {}", request.getRequestURI());
            }
        } catch (JwtException ex) {
            // Token validation failed (expired, malformed, invalid signature, etc.)
            log.warn("JWT validation failed for request to {}: {}", request.getRequestURI(), ex.getMessage());
            // Do not set authentication; let the request proceed as unauthenticated.
            // Spring Security's exception handling will return 401 if the endpoint requires authentication.
        } catch (Exception ex) {
            // Unexpected error during authentication
            log.error("Unexpected error during JWT authentication for request to {}: {}", 
                request.getRequestURI(), ex.getMessage(), ex);
        }

        // 3. Continue the filter chain (always proceed, even if authentication failed)
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT token from the "Authorization" header.
     * Expects the header format: "Bearer <token>"
     *
     * @param request The HTTP request.
     * @return The JWT token string, or null if not found or malformed.
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7); // Remove "Bearer " prefix
            log.debug("Extracted JWT token from Authorization header for request to: {}", request.getRequestURI());
            return token;
        }

        return null;
    }

    /**
     * Authenticates the user based on the provided JWT token.
     * Validates the token, loads user details, and sets up the security context.
     *
     * @param jwt     The JWT token string.
     * @param request The HTTP request (used to set authentication details).
     */
    private void authenticateUser(String jwt, HttpServletRequest request) {
        // 1. Validate the JWT token
        if (!jwtTokenProvider.validateToken(jwt)) {
            log.warn("Invalid JWT token detected for request to: {}", request.getRequestURI());
            return; // Token is invalid; do not authenticate
        }

        // 2. Extract user email from the token
        String userEmail = jwtTokenProvider.getEmailFromToken(jwt);
        log.debug("JWT token valid. Extracted user email: {}", userEmail);

        // 3. Check if the user is already authenticated in the current security context
        //    (to avoid redundant database lookups if this filter runs multiple times)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            log.debug("User {} is already authenticated in security context.", userEmail);
            return;
        }

        // 4. Load user details from the database
        //    CustomUserDetailsService will fetch the User entity and convert it to UserDetails
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(userEmail);

        // 5. Create an authentication token with the user's details and authorities (roles)
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            userDetails,
            null, // No credentials needed (token-based authentication)
            userDetails.getAuthorities() // User's roles/authorities for authorization checks
        );

        // 6. Set additional details (e.g., IP address, session ID) from the request
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // 7. Set the authentication in the security context
        //    This makes the user "logged in" for the duration of this request.
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.info("User {} authenticated successfully via JWT for request to: {}", userEmail, request.getRequestURI());
    }

    /**
     * Optionally, you can override this method to skip filtering for certain requests
     * (e.g., public endpoints like /api/auth/login, /api/auth/register).
     * However, it's generally better to configure this in the SecurityConfig.
     *
     * @param request The HTTP request.
     * @return true to skip this filter, false to execute it.
     * @throws ServletException if an error occurs.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Example: Skip filtering for public authentication endpoints
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/login") || 
               path.startsWith("/api/auth/register") ||
               path.startsWith("/api/auth/refresh") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs/");
    }
}

