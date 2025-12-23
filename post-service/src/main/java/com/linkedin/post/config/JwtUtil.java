package com.linkedin.post.config;

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
 * Utility class for JWT token operations.
 * 
 * Purpose:
 * Extracts user ID from JWT tokens for authentication.
 * Used by controllers to identify current user.
 * 
 * Usage:
 * <pre>
 * {@code
 * @RestController
 * public class PostController {
 *     @Autowired
 *     private JwtUtil jwtUtil;
 *     
 *     @GetMapping("/posts")
 *     public List<Post> getPosts() {
 *         Long currentUserId = jwtUtil.getCurrentUserId();
 *         return postService.getUserPosts(currentUserId);
 *     }
 * }
 * }
 * </pre>
 */
@Component
@Slf4j
public class JwtUtil {

    private final SecretKey secretKey;

    public JwtUtil(@Value("${app.security.jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Extract user ID from JWT token in SecurityContext.
     * 
     * @return Current user's ID
     * @throws SecurityException if not authenticated
     */
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }

        String token = (String) authentication.getCredentials();
        return extractUserId(token);
    }

    /**
     * Extract user ID from JWT token string.
     * 
     * @param token JWT token
     * @return User ID from token claims
     */
    public Long extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return claims.get("userId", Long.class);
    }
}

