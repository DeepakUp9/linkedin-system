package com.linkedin.connection.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for extracting user information from JWT tokens.
 * 
 * Purpose:
 * In a microservices architecture, user authentication is handled by a central
 * service (user-service). Other services receive JWT tokens and need to extract
 * user information (like userId) from them.
 * 
 * How JWT Works:
 * 
 * 1. User logs in to user-service:
 *    POST /api/auth/login
 *    { "email": "john@example.com", "password": "secret" }
 * 
 * 2. User-service validates credentials and creates JWT:
 *    JWT = Header.Payload.Signature
 *    
 *    Payload (decoded):
 *    {
 *      "sub": "john@example.com",
 *      "userId": 123,
 *      "email": "john@example.com",
 *      "roles": ["USER"],
 *      "iat": 1639448000,   // Issued at
 *      "exp": 1639534400    // Expires at
 *    }
 * 
 * 3. User makes request to connection-service:
 *    POST /api/connections/requests
 *    Authorization: Bearer <JWT>
 * 
 * 4. Spring Security validates JWT signature
 * 
 * 5. Connection-service extracts userId from JWT:
 *    Long userId = jwtUtil.getCurrentUserId();
 *    // Returns 123 (from JWT payload)
 * 
 * Security:
 * - JWT is signed with a secret key
 * - Only services with the same secret can validate/decode it
 * - If token is tampered with, signature verification fails
 * 
 * @see com.linkedin.connection.controller.ConnectionController#getCurrentUserId()
 * @author LinkedIn System
 * @version 1.0
 */
@Component
@Slf4j
public class JwtUtil {

    /**
     * The secret key used to sign and verify JWT tokens.
     * MUST be the same across all microservices!
     * 
     * Security Requirements:
     * - For HS512 algorithm: Minimum 512 bits (64 bytes)
     * - Should be stored in environment variable in production
     * - Never commit to version control
     */
    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    /**
     * Extracts the current user's ID from the JWT token in the SecurityContext.
     * 
     * Flow:
     * 1. Get Authentication from SecurityContext (set by Spring Security)
     * 2. Extract JWT token string from Authentication
     * 3. Parse JWT and extract claims (payload)
     * 4. Get "userId" claim from payload
     * 5. Return as Long
     * 
     * Example:
     * <pre>
     * {@code
     * @GetMapping("/my-connections")
     * public List<Connection> getMyConnections() {
     *     Long userId = jwtUtil.getCurrentUserId();
     *     return connectionService.getConnectionsByUserId(userId);
     * }
     * }
     * </pre>
     * 
     * @return The ID of the currently authenticated user
     * @throws RuntimeException if no authentication found or userId not in token
     */
    public Long getCurrentUserId() {
        try {
            // Step 1: Get Authentication from SecurityContext
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                log.error("No authenticated user found in SecurityContext");
                throw new RuntimeException("User not authenticated");
            }
            
            // Step 2: Extract JWT token string
            // In Spring Security, the token is stored in credentials
            String token = null;
            
            if (authentication.getCredentials() != null) {
                token = authentication.getCredentials().toString();
            } else if (authentication.getPrincipal() != null) {
                // Fallback: Try to get from principal
                token = authentication.getPrincipal().toString();
            }
            
            if (token == null || token.isEmpty()) {
                log.error("No JWT token found in authentication");
                throw new RuntimeException("JWT token not found");
            }
            
            // Step 3: Parse JWT and extract claims
            Claims claims = extractAllClaims(token);
            
            // Step 4: Get userId claim
            // The claim name must match what user-service puts in the JWT
            Object userIdObj = claims.get("userId");
            
            if (userIdObj == null) {
                log.error("userId claim not found in JWT token");
                throw new RuntimeException("userId not found in JWT");
            }
            
            // Step 5: Convert to Long and return
            Long userId;
            if (userIdObj instanceof Integer) {
                userId = ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof Long) {
                userId = (Long) userIdObj;
            } else {
                userId = Long.parseLong(userIdObj.toString());
            }
            
            log.debug("Extracted userId {} from JWT token", userId);
            return userId;
            
        } catch (Exception ex) {
            log.error("Error extracting userId from JWT: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to extract user ID from JWT: " + ex.getMessage(), ex);
        }
    }

    /**
     * Extracts the current user's email from the JWT token.
     * 
     * @return The email of the currently authenticated user
     */
    public String getCurrentUserEmail() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new RuntimeException("User not authenticated");
            }
            
            String token = authentication.getCredentials() != null 
                ? authentication.getCredentials().toString() 
                : authentication.getPrincipal().toString();
            
            Claims claims = extractAllClaims(token);
            
            // Email is typically in the "sub" (subject) claim
            String email = claims.getSubject();
            
            if (email == null || email.isEmpty()) {
                // Fallback: Try "email" claim
                email = (String) claims.get("email");
            }
            
            if (email == null || email.isEmpty()) {
                throw new RuntimeException("Email not found in JWT");
            }
            
            log.debug("Extracted email {} from JWT token", email);
            return email;
            
        } catch (Exception ex) {
            log.error("Error extracting email from JWT: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to extract email from JWT: " + ex.getMessage(), ex);
        }
    }

    /**
     * Parses JWT token and extracts all claims (payload).
     * 
     * Technical Details:
     * - Uses JJWT library to parse JWT
     * - Verifies signature using the secret key
     * - If signature is invalid, throws exception
     * 
     * @param token JWT token string (without "Bearer " prefix)
     * @return Claims object containing all JWT payload data
     */
    private Claims extractAllClaims(String token) {
        try {
            // Remove "Bearer " prefix if present
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            
            // Create secret key from the configured secret
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            
            // Parse and verify JWT
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
                    
        } catch (Exception ex) {
            log.error("Failed to parse JWT token: {}", ex.getMessage());
            throw new RuntimeException("Invalid JWT token", ex);
        }
    }

    /**
     * Validates if a JWT token is still valid (not expired).
     * 
     * @param token JWT token string
     * @return true if valid, false if expired or invalid
     */
    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception ex) {
            log.warn("JWT token validation failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Gets the expiration time of a JWT token.
     * 
     * @param token JWT token string
     * @return Expiration date as timestamp
     */
    public java.util.Date getExpirationDate(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getExpiration();
    }

    /**
     * Checks if the current user has a specific role.
     * 
     * @param role Role to check (e.g., "ADMIN", "USER")
     * @return true if user has the role, false otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean hasRole(String role) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null) {
                return false;
            }
            
            String token = authentication.getCredentials() != null 
                ? authentication.getCredentials().toString() 
                : authentication.getPrincipal().toString();
            
            Claims claims = extractAllClaims(token);
            
            // Roles might be stored as array or comma-separated string
            Object rolesObj = claims.get("roles");
            
            if (rolesObj instanceof java.util.List) {
                java.util.List<String> roles = (java.util.List<String>) rolesObj;
                return roles.contains(role);
            } else if (rolesObj instanceof String) {
                String roles = (String) rolesObj;
                return roles.contains(role);
            }
            
            return false;
            
        } catch (Exception ex) {
            log.error("Error checking role: {}", ex.getMessage());
            return false;
        }
    }
}

