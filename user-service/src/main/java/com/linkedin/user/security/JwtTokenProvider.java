package com.linkedin.user.security;

import com.linkedin.user.model.User;
import com.linkedin.user.model.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT Token Provider for generating and validating JSON Web Tokens.
 * 
 * This class is the heart of our authentication system. It handles:
 * - Token generation (when user logs in)
 * - Token validation (on every protected request)
 * - User information extraction from tokens
 * 
 * What is JWT?
 * 
 * JWT (JSON Web Token) is a compact, URL-safe token format for transmitting
 * information between parties. It's the industry standard for stateless authentication.
 * 
 * JWT Structure (3 parts separated by dots):
 * 
 * eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huQGV4YW1wbGUuY29tIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
 * │                     │                                      │
 * │ Header             │ Payload (Claims)                     │ Signature
 * │ {"alg":"HS256"}    │ {"sub":"john@example.com",          │ HMAC-SHA256(
 * │                     │  "exp":1735567200}                  │   base64(header) + "." +
 * │                     │                                      │   base64(payload),
 * │                     │                                      │   secret)
 * 
 * 1. Header: Algorithm and token type
 * 2. Payload: User data (claims) - email, roles, expiration
 * 3. Signature: Cryptographic signature to verify authenticity
 * 
 * Why JWT for Authentication?
 * 
 * Traditional Session-Based (Old Way):
 * - Server stores session data in memory/database
 * - Client gets session ID cookie
 * - Every request: lookup session in database
 * - Doesn't scale well (server must store all sessions)
 * - Sticky sessions needed for load balancing
 * 
 * JWT-Based (Modern Way):
 * - Server doesn't store anything (stateless)
 * - Client gets JWT token
 * - Every request: verify token signature (no database lookup)
 * - Scales horizontally (any server can verify token)
 * - Perfect for microservices
 * 
 * Authentication Flow:
 * 
 * <pre>
 * {@code
 * 1. Login:
 *    POST /api/auth/login
 *    {email: "john@example.com", password: "pass123"}
 *    ↓
 *    Server validates credentials
 *    ↓
 *    JwtTokenProvider.generateToken("john@example.com")
 *    ↓
 *    Returns: {"token": "eyJhbGciOiJIUzI1NiJ9..."}
 * 
 * 2. Subsequent Requests:
 *    GET /api/users/123
 *    Header: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
 *    ↓
 *    JwtTokenProvider.validateToken(token)
 *    ↓
 *    JwtTokenProvider.getUsernameFromToken(token)
 *    ↓
 *    Load user from database
 *    ↓
 *    Process request
 * }
 * </pre>
 * 
 * Security Considerations:
 * 
 * 1. Secret Key:
 *    - Must be strong (256+ bits for HS256)
 *    - Must be kept secret (never commit to version control)
 *    - Should be different per environment (dev, staging, prod)
 *    - Rotate periodically
 * 
 * 2. Token Expiration:
 *    - Access tokens: Short-lived (15 minutes - 1 hour)
 *    - Refresh tokens: Long-lived (7-30 days)
 *    - Balance security vs user experience
 * 
 * 3. Claims:
 *    - Don't put sensitive data in payload (it's base64, not encrypted!)
 *    - Only put: user ID, email, roles
 *    - Never: password, credit card, SSN
 * 
 * 4. Token Storage (Client):
 *    - localStorage: Vulnerable to XSS attacks
 *    - sessionStorage: Lost on tab close
 *    - HttpOnly cookie: Best for web (immune to XSS)
 *    - Memory only: Best for SPA (refresh on reload)
 * 
 * JJWT Library:
 * 
 * We use io.jsonwebtoken (JJWT) - the most popular Java JWT library:
 * - Type-safe builder API
 * - Automatic signature verification
 * - Built-in claim validation
 * - Multiple algorithm support (HS256, RS256, etc.)
 * 
 * Configuration (application.yml):
 * <pre>
 * {@code
 * jwt:
 *   secret: your-256-bit-secret-key-here-make-it-long-and-random
 *   expiration: 3600000  # 1 hour in milliseconds
 * }
 * </pre>
 * 
 * @see <a href="https://jwt.io">JWT.io - JWT Debugger</a>
 * @see <a href="https://github.com/jwtk/jjwt">JJWT Library</a>
 */
@Component
@Slf4j
public class JwtTokenProvider {

    /**
     * Secret key for signing tokens.
     * 
     * This is injected from application.yml: app.security.jwt.secret
     * 
     * Requirements:
     * - Minimum 256 bits (32 characters) for HS256
     * - Should be cryptographically random
     * - Must be kept secret (use environment variables in production)
     * 
     * Example generation (don't use this in production!):
     * openssl rand -base64 32
     * 
     * Production:
     * - Store in environment variable: JWT_SECRET
     * - Or AWS Secrets Manager / HashiCorp Vault
     * - Rotate periodically (requires token invalidation strategy)
     */
    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    /**
     * Token expiration time in milliseconds.
     * 
     * Injected from application.yml: app.security.jwt.expiration
     * 
     * Common values:
     * - 900000 (15 minutes) - High security
     * - 3600000 (1 hour) - Balanced
     * - 86400000 (24 hours) - User convenience
     * 
     * Trade-offs:
     * - Short expiration: More secure, but frequent re-login
     * - Long expiration: Better UX, but token theft risk
     * 
     * Best Practice:
     * - Short-lived access token (15-60 min)
     * - Long-lived refresh token (7-30 days)
     * - Implement token refresh mechanism
     */
    @Value("${app.security.jwt.expiration}")
    private long jwtExpirationMs;

    /**
     * Generates a JWT token for the given username (email).
     * 
     * This method is called after successful login to create an access token.
     * 
     * Token Structure:
     * <pre>
     * {@code
     * Header:
     * {
     *   "alg": "HS256",        // Algorithm: HMAC-SHA256
     *   "typ": "JWT"           // Type: JSON Web Token
     * }
     * 
     * Payload (Claims):
     * {
     *   "sub": "john@example.com",          // Subject: User identifier
     *   "iat": 1703091234,                  // Issued At: Token creation time
     *   "exp": 1703094834                   // Expiration: When token expires
     * }
     * 
     * Signature:
     * HMACSHA256(
     *   base64UrlEncode(header) + "." + base64UrlEncode(payload),
     *   secret
     * )
     * }
     * </pre>
     * 
     * Process:
     * 1. Create claims (sub, iat, exp)
     * 2. Sign with secret key using HS256 algorithm
     * 3. Encode to compact string (header.payload.signature)
     * 
     * Example Usage:
     * <pre>
     * {@code
     * // After successful login:
     * String token = jwtTokenProvider.generateToken("john@example.com");
     * // Returns: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huQGV4YW1wbGUuY29tIiwiaWF0IjoxNzAzMDkxMjM0LCJleHAiOjE3MDMwOTQ4MzR9.4n8wY..."
     * 
     * // Return to client:
     * return new AuthResponse(token, "Bearer", jwtExpirationMs);
     * }
     * </pre>
     * 
     * Security Notes:
     * - Token is signed, not encrypted (payload is readable!)
     * - Don't put sensitive data in claims
     * - Signature prevents tampering
     * 
     * @param username User's email (unique identifier)
     * @return JWT token as compact string
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        log.debug("Generating JWT token for user: {}", username);

        // Create secret key from configured secret
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        // Build JWT with claims
        String token = Jwts.builder()
            .setSubject(username)                    // "sub" claim: user email
            .setIssuedAt(now)                        // "iat" claim: current time
            .setExpiration(expiryDate)               // "exp" claim: expiration time
            .signWith(key, SignatureAlgorithm.HS256) // Sign with HS256 algorithm
            .compact();                              // Build compact string

        log.debug("JWT token generated successfully, expires at: {}", expiryDate);
        return token;
    }

    /**
     * Extracts username (email) from JWT token.
     * 
     * This method parses the token and retrieves the "sub" (subject) claim,
     * which contains the user's email.
     * 
     * Process:
     * 1. Parse token using secret key
     * 2. Verify signature
     * 3. Extract claims
     * 4. Return "sub" claim value
     * 
     * Example:
     * <pre>
     * {@code
     * String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huQGV4YW1wbGUuY29tIn0...";
     * String email = jwtTokenProvider.getUsernameFromToken(token);
     * // Returns: "john@example.com"
     * }
     * </pre>
     * 
     * Used in:
     * - Authentication filter (to identify current user)
     * - Loading user details from database
     * - Authorization checks
     * 
     * Note: This method assumes token is already validated.
     * Always call validateToken() first in production code.
     * 
     * @param token JWT token string
     * @return Username (email) from token's subject claim
     * @throws JwtException if token is invalid or expired
     */
    public String getUsernameFromToken(String token) {
        log.debug("Extracting username from JWT token");

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        // Parse token and extract claims
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();

        String username = claims.getSubject();
        log.debug("Username extracted from token: {}", username);

        return username;
    }

    /**
     * Validates a JWT token.
     * 
     * Checks if token is:
     * 1. Properly formatted (3 parts: header.payload.signature)
     * 2. Signed with correct secret key (signature valid)
     * 3. Not expired (exp claim < current time)
     * 4. Not tampered with (signature verification)
     * 
     * Returns true if valid, false otherwise.
     * Logs specific error for debugging.
     * 
     * Validation Process:
     * <pre>
     * {@code
     * JWT Token: header.payload.signature
     * 
     * 1. Parse header and payload (Base64 decode)
     * 2. Recompute signature using secret key
     * 3. Compare computed signature with token signature
     *    - Match? Signature valid (not tampered)
     *    - No match? Invalid token
     * 4. Check expiration claim
     *    - exp > now? Valid
     *    - exp <= now? Expired
     * }
     * </pre>
     * 
     * Possible Errors:
     * 
     * 1. SignatureException:
     *    - Token signature doesn't match
     *    - Means: Token was tampered with OR wrong secret key
     *    - Action: Reject token
     * 
     * 2. MalformedJwtException:
     *    - Token format is invalid
     *    - Means: Not a valid JWT structure
     *    - Action: Reject token
     * 
     * 3. ExpiredJwtException:
     *    - Token has expired (exp claim passed)
     *    - Means: User needs to re-login or refresh token
     *    - Action: Return 401, prompt re-login
     * 
     * 4. UnsupportedJwtException:
     *    - Token uses unsupported algorithm
     *    - Means: Token from incompatible system
     *    - Action: Reject token
     * 
     * 5. IllegalArgumentException:
     *    - Token is null or empty
     *    - Means: Missing token
     *    - Action: Reject request
     * 
     * Example Usage:
     * <pre>
     * {@code
     * String token = request.getHeader("Authorization").replace("Bearer ", "");
     * 
     * if (jwtTokenProvider.validateToken(token)) {
     *     String username = jwtTokenProvider.getUsernameFromToken(token);
     *     // Load user and authenticate
     * } else {
     *     // Return 401 Unauthorized
     * }
     * }
     * </pre>
     * 
     * Security Note:
     * - This method uses constant-time comparison (via library)
     * - Prevents timing attacks
     * - Don't implement custom signature verification
     * 
     * @param token JWT token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            log.debug("Validating JWT token");

            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            // Parse and validate token
            // This checks:
            // 1. Token format
            // 2. Signature validity
            // 3. Expiration
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);

            log.debug("JWT token is valid");
            return true;

        } catch (SignatureException ex) {
            log.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token format: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("JWT token is expired: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("JWT token is unsupported: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }

        return false;
    }

    /**
     * Gets the token expiration time in milliseconds.
     * 
     * Useful for:
     * - Returning expiration info to client
     * - Client can know when to refresh token
     * - UI can show "session expires in X minutes"
     * 
     * Example Response:
     * <pre>
     * {@code
     * {
     *   "token": "eyJhbGciOiJIUzI1NiJ9...",
     *   "type": "Bearer",
     *   "expiresIn": 3600000  // 1 hour in milliseconds
     * }
     * }
     * </pre>
     * 
     * @return Expiration time in milliseconds
     */
    public long getExpirationMs() {
        return jwtExpirationMs;
    }

    // ==================== ENHANCED METHODS FOR AUTHENTICATION SYSTEM ====================

    /**
     * Generates a JWT token with user details including roles and userId.
     * 
     * This is an enhanced version of generateToken() that includes:
     * - sub: User email (subject)
     * - userId: User ID (for faster lookups)
     * - roles: User roles (for authorization)
     * - iat: Issued at timestamp
     * - exp: Expiration timestamp
     * 
     * Token Structure:
     * <pre>
     * {@code
     * Payload (Claims):
     * {
     *   "sub": "john@example.com",
     *   "userId": 123,
     *   "roles": ["USER"],  // or ["USER", "ADMIN"]
     *   "iat": 1703091234,
     *   "exp": 1703094834
     * }
     * }
     * </pre>
     * 
     * Why include userId and roles?
     * - userId: Faster user lookup (no need to query by email)
     * - roles: Authorization without database query
     * - Reduces database calls on every request
     * 
     * Security Note:
     * - Token is signed, so roles cannot be tampered with
     * - Payload is readable (Base64), so don't include sensitive data
     * - Short expiration (15 min) limits impact of stolen tokens
     * 
     * Example Usage:
     * <pre>
     * {@code
     * User user = userRepository.findByEmail("john@example.com").orElseThrow();
     * String token = jwtTokenProvider.generateTokenForUser(user);
     * 
     * // Token includes:
     * // - Email: john@example.com
     * // - User ID: 123
     * // - Roles: [USER]
     * }
     * </pre>
     * 
     * @param user User entity with email, id, and roles
     * @return JWT token as compact string with all user claims
     */
    public String generateTokenForUser(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        log.debug("Generating JWT token for user: {} (ID: {})", user.getEmail(), user.getId());

        // Convert Set<UserRole> to List<String> for JWT claims
        List<String> roles = user.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toList());

        // Create secret key
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        // Build JWT with enhanced claims
        String token = Jwts.builder()
                .setSubject(user.getEmail())                  // "sub": email
                .claim("userId", user.getId())                // "userId": user ID
                .claim("roles", roles)                        // "roles": ["USER"] or ["USER", "ADMIN"]
                .setIssuedAt(now)                             // "iat": current time
                .setExpiration(expiryDate)                    // "exp": expiration time
                .signWith(key, SignatureAlgorithm.HS512)      // Sign with HS512 (more secure than HS256)
                .compact();

        log.debug("JWT token generated for user {} with roles: {}, expires at: {}", 
                  user.getEmail(), roles, expiryDate);
        
        return token;
    }

    /**
     * Extracts user ID from JWT token.
     * 
     * This is useful for:
     * - Quick user lookup by ID (faster than by email)
     * - Avoiding email-to-ID conversion
     * - Database optimization
     * 
     * Example:
     * <pre>
     * {@code
     * String token = "eyJhbGci...";
     * Long userId = jwtTokenProvider.getUserIdFromToken(token);
     * 
     * // Direct lookup by ID (primary key - fastest)
     * User user = userRepository.findById(userId).orElseThrow();
     * 
     * // vs. lookup by email (requires index scan)
     * // User user = userRepository.findByEmail(email).orElseThrow();
     * }
     * </pre>
     * 
     * @param token JWT token string
     * @return User ID from token's userId claim
     * @throws JwtException if token is invalid or userId claim is missing
     */
    public Long getUserIdFromToken(String token) {
        log.debug("Extracting user ID from JWT token");

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        // Get userId claim and convert to Long
        Object userIdClaim = claims.get("userId");
        if (userIdClaim == null) {
            throw new JwtException("Token does not contain userId claim");
        }

        // Handle different numeric types (Integer or Long)
        Long userId;
        if (userIdClaim instanceof Integer) {
            userId = ((Integer) userIdClaim).longValue();
        } else if (userIdClaim instanceof Long) {
            userId = (Long) userIdClaim;
        } else {
            throw new JwtException("userId claim is not a valid number: " + userIdClaim.getClass());
        }

        log.debug("User ID extracted from token: {}", userId);
        return userId;
    }

    /**
     * Extracts roles from JWT token.
     * 
     * This is useful for:
     * - Authorization without database query
     * - Checking permissions in filters/interceptors
     * - Role-based UI rendering
     * 
     * Example:
     * <pre>
     * {@code
     * String token = "eyJhbGci...";
     * Set<UserRole> roles = jwtTokenProvider.getRolesFromToken(token);
     * 
     * // Check if user is admin
     * if (roles.contains(UserRole.ADMIN)) {
     *     // Allow admin operation
     * }
     * 
     * // Or use in Spring Security
     * Set<GrantedAuthority> authorities = roles.stream()
     *     .map(role -> new SimpleGrantedAuthority(role.getAuthority()))
     *     .collect(Collectors.toSet());
     * }
     * </pre>
     * 
     * Security Note:
     * - Roles in token are trusted (signed by server)
     * - Always validate token signature first
     * - For critical operations, recheck roles from database
     * 
     * @param token JWT token string
     * @return Set of user roles from token's roles claim
     * @throws JwtException if token is invalid or roles claim is missing
     */
    @SuppressWarnings("unchecked")
    public Set<UserRole> getRolesFromToken(String token) {
        log.debug("Extracting roles from JWT token");

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        // Get roles claim
        Object rolesClaim = claims.get("roles");
        if (rolesClaim == null) {
            log.warn("Token does not contain roles claim, defaulting to USER role");
            return Set.of(UserRole.USER);
        }

        // Convert List<String> to Set<UserRole>
        List<String> roleNames;
        if (rolesClaim instanceof List) {
            roleNames = (List<String>) rolesClaim;
        } else {
            throw new JwtException("roles claim is not a valid list: " + rolesClaim.getClass());
        }

        Set<UserRole> roles = roleNames.stream()
                .map(UserRole::valueOf)
                .collect(Collectors.toSet());

        log.debug("Roles extracted from token: {}", roles);
        return roles;
    }

    /**
     * Extracts all claims from JWT token.
     * 
     * This gives access to the complete payload for custom processing.
     * 
     * Available claims:
     * - sub: User email (subject)
     * - userId: User ID
     * - roles: User roles
     * - iat: Issued at timestamp
     * - exp: Expiration timestamp
     * 
     * Example:
     * <pre>
     * {@code
     * String token = "eyJhbGci...";
     * Claims claims = jwtTokenProvider.getAllClaimsFromToken(token);
     * 
     * String email = claims.getSubject();
     * Long userId = claims.get("userId", Long.class);
     * List<String> roles = claims.get("roles", List.class);
     * Date issuedAt = claims.getIssuedAt();
     * Date expiration = claims.getExpiration();
     * 
     * // Calculate remaining time
     * long remaining = expiration.getTime() - System.currentTimeMillis();
     * System.out.println("Token expires in: " + remaining + " ms");
     * }
     * </pre>
     * 
     * @param token JWT token string
     * @return Claims object containing all token claims
     * @throws JwtException if token is invalid
     */
    public Claims getAllClaimsFromToken(String token) {
        log.debug("Extracting all claims from JWT token");

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Checks if a token is expired.
     * 
     * This is useful for:
     * - Proactive token refresh (client-side)
     * - Custom expiration handling
     * - Logging/monitoring
     * 
     * Example:
     * <pre>
     * {@code
     * String token = "eyJhbGci...";
     * 
     * if (jwtTokenProvider.isTokenExpired(token)) {
     *     // Refresh token or redirect to login
     *     String newToken = refreshToken(refreshToken);
     * } else {
     *     // Continue using token
     *     makeApiCall(token);
     * }
     * }
     * </pre>
     * 
     * Note: validateToken() already checks expiration.
     * This method is for cases where you want explicit expiration check.
     * 
     * @param token JWT token string
     * @return true if token is expired, false otherwise
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            boolean expired = expiration.before(new Date());
            
            log.debug("Token expiration check: expired={}, expiryDate={}", expired, expiration);
            return expired;
        } catch (ExpiredJwtException ex) {
            log.debug("Token is expired: {}", ex.getMessage());
            return true;
        } catch (JwtException ex) {
            log.error("Error checking token expiration: {}", ex.getMessage());
            return true; // Consider invalid tokens as expired
        }
    }

    /**
     * Gets the remaining time until token expires (in milliseconds).
     * 
     * This is useful for:
     * - Displaying "Session expires in X minutes" to user
     * - Proactive token refresh (e.g., refresh when < 2 min remaining)
     * - Session management UI
     * 
     * Example:
     * <pre>
     * {@code
     * String token = "eyJhbGci...";
     * long remaining = jwtTokenProvider.getTokenRemainingTime(token);
     * 
     * if (remaining < 2 * 60 * 1000) {  // Less than 2 minutes
     *     // Proactively refresh token
     *     refreshAccessToken();
     * }
     * 
     * // Or show to user
     * int minutes = (int) (remaining / 1000 / 60);
     * showMessage("Session expires in " + minutes + " minutes");
     * }
     * </pre>
     * 
     * @param token JWT token string
     * @return Remaining time in milliseconds, or 0 if expired/invalid
     */
    public long getTokenRemainingTime(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            long remaining = expiration.getTime() - System.currentTimeMillis();
            
            // Return 0 if already expired (don't return negative)
            return Math.max(0, remaining);
        } catch (JwtException ex) {
            log.error("Error getting token remaining time: {}", ex.getMessage());
            return 0;
        }
    }

    /**
     * Gets the expiration time in seconds (for client response).
     * 
     * This is used in LoginResponse/RegisterResponse to tell client
     * how long the access token is valid.
     * 
     * Example:
     * <pre>
     * {@code
     * LoginResponse response = LoginResponse.builder()
     *     .accessToken(token)
     *     .tokenType("Bearer")
     *     .expiresIn(jwtTokenProvider.getExpirationInSeconds())  // 900 seconds
     *     .build();
     * }
     * </pre>
     * 
     * @return Expiration time in seconds (e.g., 900 for 15 minutes)
     */
    public int getExpirationInSeconds() {
        return (int) (jwtExpirationMs / 1000);
    }
}

