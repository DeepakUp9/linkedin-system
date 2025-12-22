package com.linkedin.notification.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

/**
 * Utility class for JWT token operations.
 * 
 * Purpose:
 * - Extract user ID from JWT token
 * - Validate JWT tokens
 * - Parse JWT claims
 * 
 * Usage in Controllers:
 * <pre>
 * {@code
 * @Autowired
 * private JwtUtil jwtUtil;
 * 
 * public void someMethod() {
 *     Long userId = jwtUtil.getCurrentUserId();
 *     // Use userId to fetch user's data
 * }
 * }
 * </pre>
 * 
 * JWT Structure:
 * Header.Payload.Signature
 * 
 * Payload Contains:
 * - userId: Long (our custom claim)
 * - email: String
 * - exp: Expiration timestamp
 * - iat: Issued at timestamp
 * 
 * @see com.linkedin.notification.controller.NotificationController
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    /**
     * Get current authenticated user's ID from JWT token.
     * 
     * Process:
     * 1. Get Authentication from SecurityContext
     * 2. Extract JWT token from credentials
     * 3. Parse token and extract userId claim
     * 4. Return user ID
     * 
     * @return Current user's ID
     * @throws SecurityException if not authenticated or invalid token
     */
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            log.warn("No authenticated user found in SecurityContext or user is anonymous.");
            throw new SecurityException("User not authenticated.");
        }

        // Extract JWT token from credentials
        String jwtToken = (String) authentication.getCredentials();
        if (jwtToken == null || jwtToken.isEmpty()) {
            log.error("JWT token not found in SecurityContext credentials for authenticated user.");
            throw new SecurityException("JWT token not found.");
        }

        try {
            // Extract userId from token
            return extractClaim(jwtToken, claims -> claims.get("userId", Long.class));
        } catch (Exception e) {
            log.error("Failed to extract userId from JWT token: {}", e.getMessage());
            throw new SecurityException("Invalid or expired JWT token.");
        }
    }

    /**
     * Extract a specific claim from JWT token.
     * 
     * @param token JWT token
     * @param claimsResolver Function to extract desired claim
     * @param <T> Type of claim
     * @return Extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract all claims from JWT token.
     * 
     * @param token JWT token
     * @return All claims
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Get signing key for JWT verification.
     * 
     * @return Secret key
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Check if token is expired.
     * 
     * @param token JWT token
     * @return true if expired
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extract expiration date from token.
     * 
     * @param token JWT token
     * @return Expiration date
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}

