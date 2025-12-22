package com.linkedin.user.service;

import com.linkedin.common.exceptions.ValidationException;
import com.linkedin.user.dto.UserResponse;
import com.linkedin.user.dto.auth.*;
import com.linkedin.user.mapper.UserMapper;
import com.linkedin.user.model.RefreshToken;
import com.linkedin.user.model.User;
import com.linkedin.user.patterns.factory.UserFactory;
import com.linkedin.user.patterns.strategy.ProfileValidationStrategy;
import com.linkedin.user.patterns.strategy.ValidationStrategyFactory;
import com.linkedin.user.repository.UserRepository;
import com.linkedin.user.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for handling authentication operations.
 * 
 * Purpose:
 * This service orchestrates the complete authentication flow for the application.
 * It coordinates multiple components to provide login, registration, token refresh,
 * and logout functionality.
 * 
 * Components Used:
 * 1. UserRepository - Database access for users
 * 2. UserFactory - Creates User entities with proper validation
 * 3. JwtTokenProvider - Generates and validates JWT access tokens
 * 4. RefreshTokenService - Manages long-lived refresh tokens
 * 5. PasswordEncoder - BCrypt password hashing and verification
 * 6. UserMapper - Maps User entities to DTOs
 * 7. AuthenticationManager - Spring Security authentication
 * 8. ValidationStrategyFactory - Account type validation
 * 
 * Authentication Flows:
 * 
 * 1. LOGIN FLOW:
 * <pre>
 * {@code
 * Client sends: { email, password }
 *   ↓
 * AuthenticationService.login()
 *   ↓
 * 1. AuthenticationManager authenticates credentials
 * 2. Load user from database
 * 3. Generate JWT access token (15 min)
 * 4. Create refresh token (7 days)
 * 5. Return LoginResponse with both tokens
 *   ↓
 * Client stores: accessToken, refreshToken
 * Client uses: Authorization: Bearer <accessToken>
 * }
 * </pre>
 * 
 * 2. REGISTER FLOW:
 * <pre>
 * {@code
 * Client sends: { email, password, name, ... }
 *   ↓
 * AuthenticationService.register()
 *   ↓
 * 1. Check if email already exists
 * 2. Create user via UserFactory (hashes password)
 * 3. Validate via Strategy Pattern (BASIC or PREMIUM)
 * 4. Save user to database
 * 5. Generate JWT access token
 * 6. Create refresh token
 * 7. Return RegisterResponse (auto-login)
 *   ↓
 * Client is immediately logged in
 * }
 * </pre>
 * 
 * 3. REFRESH FLOW:
 * <pre>
 * {@code
 * Access token expired after 15 minutes
 *   ↓
 * Client sends: { refreshToken }
 *   ↓
 * AuthenticationService.refresh()
 *   ↓
 * 1. Validate refresh token (exists? expired? user active?)
 * 2. Generate new JWT access token
 * 3. Check if refresh token needs rotation (< 1 day left)
 * 4. If yes, create new refresh token, delete old one
 * 5. Return new access token (+ maybe new refresh token)
 *   ↓
 * Client continues session without re-login
 * }
 * </pre>
 * 
 * 4. LOGOUT FLOW:
 * <pre>
 * {@code
 * Client sends: { refreshToken }
 *   ↓
 * AuthenticationService.logout()
 *   ↓
 * 1. Delete refresh token from database
 * 2. Token immediately invalid
 * 3. Return success
 *   ↓
 * Client clears tokens from storage
 * Access token still valid until expiry (15 min max)
 * }
 * </pre>
 * 
 * Security Features:
 * 
 * 1. Password Security:
 *    - BCrypt hashing (work factor 10)
 *    - Password never stored in plain text
 *    - Password verification via PasswordEncoder
 * 
 * 2. Token Security:
 *    - Access tokens: Short-lived (15 min), JWT signed with secret
 *    - Refresh tokens: Long-lived (7 days), stored in database
 *    - Refresh tokens can be revoked (logout)
 *    - Token rotation for active users
 * 
 * 3. Account Validation:
 *    - Email uniqueness check
 *    - Account type validation (BASIC vs PREMIUM)
 *    - Account status check (active, verified)
 * 
 * 4. Rate Limiting (Future):
 *    - Limit login attempts per IP
 *    - Lock account after X failed attempts
 *    - Implement CAPTCHA after failures
 * 
 * Design Patterns Applied:
 * 
 * 1. Service Layer Pattern:
 *    - Encapsulates business logic
 *    - Coordinates multiple components
 *    - Transactional boundary
 * 
 * 2. Factory Pattern:
 *    - UserFactory creates User entities
 *    - Handles password hashing, defaults
 * 
 * 3. Strategy Pattern:
 *    - ProfileValidationStrategy validates based on account type
 *    - BASIC vs PREMIUM validation rules
 * 
 * 4. DTO Pattern:
 *    - Clean API contracts (Request/Response DTOs)
 *    - Separates API from domain model
 * 
 * Error Handling:
 * 
 * 1. BadCredentialsException:
 *    - Invalid email or password
 *    - Returns: 401 Unauthorized
 *    - Message: "Invalid email or password"
 * 
 * 2. ValidationException:
 *    - Email already exists
 *    - Invalid account type data
 *    - Returns: 400 Bad Request
 * 
 * 3. ResourceNotFoundException:
 *    - Refresh token not found
 *    - Returns: 404 Not Found
 * 
 * Transaction Management:
 * 
 * - Login: @Transactional for token creation
 * - Register: @Transactional for user creation + tokens
 * - Refresh: @Transactional for token rotation
 * - Logout: @Transactional for token deletion
 * 
 * If any operation fails, entire transaction rolls back.
 * 
 * Usage Examples:
 * 
 * <pre>
 * {@code
 * // In AuthController
 * 
 * @PostMapping("/login")
 * public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
 *     LoginResponse response = authenticationService.login(request);
 *     return ResponseEntity.ok(response);
 * }
 * 
 * @PostMapping("/register")
 * public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
 *     RegisterResponse response = authenticationService.register(request);
 *     return ResponseEntity.status(HttpStatus.CREATED).body(response);
 * }
 * 
 * @PostMapping("/refresh")
 * public ResponseEntity<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
 *     RefreshTokenResponse response = authenticationService.refresh(request);
 *     return ResponseEntity.ok(response);
 * }
 * 
 * @PostMapping("/logout")
 * public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
 *     authenticationService.logout(request.getRefreshToken());
 *     return ResponseEntity.noContent().build();
 * }
 * }
 * </pre>
 * 
 * @see LoginRequest
 * @see LoginResponse
 * @see RegisterRequest
 * @see RegisterResponse
 * @see RefreshTokenRequest
 * @see RefreshTokenResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuthenticationService {

    private final UserRepository userRepository;
    private final UserFactory userFactory;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final ValidationStrategyFactory validationStrategyFactory;

    // ==================== LOGIN ====================

    /**
     * Authenticates user and returns access + refresh tokens.
     * 
     * This is the main login method that handles user authentication
     * and token generation.
     * 
     * Process:
     * 1. Authenticate credentials via Spring Security
     * 2. Load user from database
     * 3. Check account status (active, verified)
     * 4. Generate JWT access token (15 min)
     * 5. Create refresh token (7 days)
     * 6. Return both tokens + user info
     * 
     * Authentication Flow:
     * <pre>
     * {@code
     * 1. Client submits credentials
     *    POST /api/auth/login
     *    { "email": "john@example.com", "password": "Pass123" }
     * 
     * 2. AuthenticationManager.authenticate()
     *    - Calls CustomUserDetailsService.loadUserByUsername()
     *    - Loads user from database
     *    - Verifies password with BCrypt
     *    - If match → Authentication successful
     *    - If no match → BadCredentialsException
     * 
     * 3. If authenticated:
     *    - Generate access token (JWT, 15 min)
     *    - Create refresh token (UUID, 7 days)
     *    - Return LoginResponse
     * 
     * 4. Client receives:
     *    {
     *      "accessToken": "eyJhbGci...",
     *      "refreshToken": "550e8400-...",
     *      "tokenType": "Bearer",
     *      "expiresIn": 900,
     *      "user": { ... }
     *    }
     * 
     * 5. Client stores tokens:
     *    localStorage.setItem('accessToken', response.accessToken);
     *    localStorage.setItem('refreshToken', response.refreshToken);
     * 
     * 6. Client uses access token for API calls:
     *    Authorization: Bearer eyJhbGci...
     * }
     * </pre>
     * 
     * Security Checks:
     * 1. Password verification (BCrypt)
     * 2. Account active check
     * 3. Email verified check (optional)
     * 
     * Token Details:
     * - Access Token: JWT, 15 minutes, contains email/userId/roles
     * - Refresh Token: UUID, 7 days, stored in database
     * 
     * Example Usage:
     * <pre>
     * {@code
     * LoginRequest request = new LoginRequest();
     * request.setEmail("john@example.com");
     * request.setPassword("Pass123");
     * 
     * try {
     *     LoginResponse response = authenticationService.login(request);
     *     // User authenticated successfully
     *     // Store tokens on client side
     * } catch (BadCredentialsException ex) {
     *     // Invalid email or password
     *     // Return 401 Unauthorized
     * }
     * }
     * </pre>
     * 
     * @param request Login credentials (email, password)
     * @return LoginResponse with access token, refresh token, and user info
     * @throws BadCredentialsException if email or password is invalid
     * @throws ValidationException if account is inactive or unverified
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getEmail());

        try {
            // 1. Authenticate credentials via Spring Security
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getEmail(),
                    request.getPassword()
                )
            );

            log.debug("Authentication successful for user: {}", request.getEmail());

            // 2. Load user from database (already loaded by UserDetailsService, but we need full entity)
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new BadCredentialsException("User not found"));

            // 3. Check account status
            if (!user.isAccountActive()) {
                log.warn("Login attempt for inactive account: {}", request.getEmail());
                throw new ValidationException(
                    "Account is inactive. Please contact support.",
                    "ACCOUNT_INACTIVE"
                );
            }

            // Optional: Check email verification
            // if (!user.hasVerifiedEmail()) {
            //     throw new ValidationException("Email not verified", "EMAIL_NOT_VERIFIED");
            // }

            // 4. Generate JWT access token
            String accessToken = jwtTokenProvider.generateTokenForUser(user);
            log.debug("Access token generated for user: {}", user.getEmail());

            // 5. Create refresh token
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
            log.debug("Refresh token created for user: {}", user.getEmail());

            // 6. Map user to DTO
            UserResponse userResponse = userMapper.toResponse(user);

            // 7. Build and return login response
            LoginResponse response = LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken.getToken())
                    .tokenType("Bearer")
                    .expiresIn(jwtTokenProvider.getExpirationInSeconds())
                    .issuedAt(LocalDateTime.now())
                    .user(userResponse)
                    .build();

            log.info("Login successful for user: {} (ID: {})", user.getEmail(), user.getId());
            return response;

        } catch (AuthenticationException ex) {
            log.warn("Login failed for user: {} - {}", request.getEmail(), ex.getMessage());
            throw new BadCredentialsException("Invalid email or password");
        }
    }

    // ==================== REGISTER ====================

    /**
     * Registers a new user and returns tokens (auto-login).
     * 
     * This method handles user registration with automatic login.
     * After successful registration, user receives tokens immediately
     * without needing to call login endpoint.
     * 
     * Process:
     * 1. Check if email already exists
     * 2. Create user via UserFactory (handles password hashing, defaults)
     * 3. Validate via Strategy Pattern (BASIC or PREMIUM validation)
     * 4. Save user to database
     * 5. Generate JWT access token
     * 6. Create refresh token
     * 7. Return tokens + user info
     * 
     * Registration Flow:
     * <pre>
     * {@code
     * 1. Client submits registration
     *    POST /api/auth/register
     *    {
     *      "email": "john@example.com",
     *      "password": "Pass123",
     *      "name": "John Doe"
     *    }
     * 
     * 2. Check email uniqueness:
     *    SELECT * FROM users WHERE email = 'john@example.com'
     *    If exists → ValidationException
     * 
     * 3. Create user (UserFactory):
     *    - Validate email format
     *    - Validate password strength
     *    - Hash password with BCrypt
     *    - Set account type (BASIC or PREMIUM)
     *    - Set defaults (isActive=true, emailVerified=false)
     * 
     * 4. Validate profile (Strategy Pattern):
     *    - BASIC: email, password, name required
     *    - PREMIUM: + headline, summary, location required
     * 
     * 5. Save to database:
     *    INSERT INTO users (email, password, name, ...) VALUES (...)
     * 
     * 6. Generate tokens:
     *    - Access token (JWT, 15 min)
     *    - Refresh token (UUID, 7 days)
     * 
     * 7. Return RegisterResponse:
     *    {
     *      "accessToken": "eyJhbGci...",
     *      "refreshToken": "550e8400-...",
     *      "tokenType": "Bearer",
     *      "expiresIn": 900,
     *      "user": {
     *        "id": 42,
     *        "email": "john@example.com",
     *        "name": "John Doe",
     *        "accountType": "BASIC",
     *        "roles": ["USER"],
     *        "isActive": true,
     *        "emailVerified": false
     *      }
     *    }
     * 
     * 8. Client is immediately logged in!
     * }
     * </pre>
     * 
     * Account Types:
     * 
     * BASIC Account (default):
     * - Required: email, password, name
     * - Optional: all other fields
     * - Free tier
     * 
     * PREMIUM Account:
     * - Required: email, password, name, headline, summary, location
     * - Paid tier
     * - Additional features
     * 
     * Validation by Strategy Pattern:
     * - Request.accountType → selects validation strategy
     * - BASIC → BasicProfileValidationStrategy
     * - PREMIUM → PremiumProfileValidationStrategy
     * 
     * Security:
     * - Password hashed with BCrypt before storage
     * - Email uniqueness enforced at database level
     * - Account type validation prevents invalid data
     * 
     * Example Usage:
     * <pre>
     * {@code
     * // Register BASIC user
     * RegisterRequest request = RegisterRequest.builder()
     *     .email("john@example.com")
     *     .password("Pass123")
     *     .name("John Doe")
     *     .build();
     * 
     * RegisterResponse response = authenticationService.register(request);
     * // User created and logged in
     * 
     * // Register PREMIUM user
     * RegisterRequest premiumRequest = RegisterRequest.builder()
     *     .email("jane@example.com")
     *     .password("Pass456")
     *     .name("Jane Smith")
     *     .accountType(AccountType.PREMIUM)
     *     .headline("Senior Software Engineer")
     *     .summary("10 years of experience...")
     *     .location("San Francisco, CA")
     *     .build();
     * 
     * RegisterResponse premiumResponse = authenticationService.register(premiumRequest);
     * }
     * </pre>
     * 
     * @param request Registration data (email, password, name, etc.)
     * @return RegisterResponse with tokens and user info (auto-login)
     * @throws ValidationException if email already exists or validation fails
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());

        // 1. Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: Email already exists - {}", request.getEmail());
            throw new ValidationException(
                "Email address is already registered",
                "USER_EMAIL_EXISTS"
            );
        }

        // 2. Determine if this is a BASIC or PREMIUM registration
        boolean isPremium = request.getAccountType() != null && 
                           request.getAccountType().name().equals("PREMIUM");

        // 3. Create user via Factory Pattern
        User user;
        if (isPremium) {
            log.debug("Creating PREMIUM user for email: {}", request.getEmail());
            user = userFactory.createPremiumUser(
                com.linkedin.user.dto.CreateUserRequest.builder()
                    .email(request.getEmail())
                    .password(request.getPassword())
                    .name(request.getName())
                    .headline(request.getHeadline())
                    .summary(request.getSummary())
                    .location(request.getLocation())
                    .phoneNumber(request.getPhoneNumber())
                    .industry(request.getIndustry())
                    .currentJobTitle(request.getCurrentJobTitle())
                    .currentCompany(request.getCurrentCompany())
                    .build()
            );
        } else {
            log.debug("Creating BASIC user for email: {}", request.getEmail());
            user = userFactory.createUser(
                com.linkedin.user.dto.CreateUserRequest.builder()
                    .email(request.getEmail())
                    .password(request.getPassword())
                    .name(request.getName())
                    .build()
            );
        }

        // 4. Validate profile using Strategy Pattern
        ProfileValidationStrategy validationStrategy = 
            validationStrategyFactory.getStrategy(user.getAccountType());
        validationStrategy.validate(user);
        log.debug("Profile validation passed for user: {}", user.getEmail());

        // 5. Save user to database
        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {} (ID: {})", savedUser.getEmail(), savedUser.getId());

        // 6. Generate JWT access token
        String accessToken = jwtTokenProvider.generateTokenForUser(savedUser);
        log.debug("Access token generated for new user: {}", savedUser.getEmail());

        // 7. Create refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser);
        log.debug("Refresh token created for new user: {}", savedUser.getEmail());

        // 8. Map user to DTO
        UserResponse userResponse = userMapper.toResponse(savedUser);

        // 9. Build and return register response (auto-login)
        RegisterResponse response = RegisterResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationInSeconds())
                .issuedAt(LocalDateTime.now())
                .user(userResponse)
                .build();

        log.info("Registration and auto-login successful for user: {}", savedUser.getEmail());
        return response;
    }

    // ==================== REFRESH TOKEN ====================

    /**
     * Refreshes access token using refresh token.
     * 
     * This method handles token refresh when the access token expires.
     * It validates the refresh token and issues a new access token.
     * If the refresh token is close to expiry, it also rotates the refresh token.
     * 
     * Process:
     * 1. Validate refresh token (exists, not expired, user active)
     * 2. Generate new JWT access token
     * 3. Check if refresh token needs rotation (< 1 day left)
     * 4. If rotation needed, create new refresh token and delete old one
     * 5. Return new access token (+ maybe new refresh token)
     * 
     * Token Refresh Flow:
     * <pre>
     * {@code
     * 1. Access token expires after 15 minutes
     *    Client gets 401 Unauthorized
     * 
     * 2. Client sends refresh token:
     *    POST /api/auth/refresh
     *    { "refreshToken": "550e8400-e29b-41d4-a716-..." }
     * 
     * 3. Validate refresh token:
     *    - Look up in database
     *    - Check expiration (< 7 days old?)
     *    - Check user still active
     * 
     * 4. Generate new access token:
     *    - New JWT with 15 min expiry
     *    - Contains latest user info (roles, etc.)
     * 
     * 5. Check refresh token age:
     *    - Age >= 6 days (< 1 day left)?
     *      → Yes: Rotate (new 7-day token)
     *      → No: Keep same token
     * 
     * 6. Return response:
     *    {
     *      "accessToken": "eyJhbGci...",  // Always new
     *      "refreshToken": "9b2d5f8a...",  // Only if rotated
     *      "tokenType": "Bearer",
     *      "expiresIn": 900
     *    }
     * 
     * 7. Client updates tokens:
     *    - Always update access token
     *    - If new refresh token provided, update it too
     * }
     * </pre>
     * 
     * Token Rotation Strategy:
     * 
     * Scenario 1: Token age < 6 days (> 1 day remaining)
     * - Keep existing refresh token
     * - Only return new access token
     * - Client continues using same refresh token
     * 
     * Scenario 2: Token age >= 6 days (< 1 day remaining)
     * - Create new refresh token (7 days from now)
     * - Delete old refresh token
     * - Return new access token + new refresh token
     * - Client MUST update refresh token
     * 
     * Benefits of Token Rotation:
     * - Active users get extended sessions (never expire)
     * - Inactive users (> 7 days) must re-login
     * - Old tokens cannot be reused (deleted)
     * - Reduces risk of token theft
     * 
     * Error Scenarios:
     * 
     * 1. Token Not Found:
     *    - Token doesn't exist in database
     *    - Maybe already used and rotated
     *    - Maybe manually deleted (logout)
     *    - Return 404: "Invalid refresh token"
     * 
     * 2. Token Expired:
     *    - Token older than 7 days
     *    - User must re-login
     *    - Return 401: "Refresh token expired"
     * 
     * 3. User Inactive:
     *    - Account disabled/suspended
     *    - Return 401: "Account inactive"
     * 
     * Example Usage:
     * <pre>
     * {@code
     * RefreshTokenRequest request = new RefreshTokenRequest();
     * request.setRefreshToken("550e8400-e29b-41d4-a716-...");
     * 
     * try {
     *     RefreshTokenResponse response = authenticationService.refresh(request);
     *     
     *     // Update access token
     *     localStorage.setItem('accessToken', response.getAccessToken());
     *     
     *     // Check if refresh token was rotated
     *     if (response.hasNewRefreshToken()) {
     *         localStorage.setItem('refreshToken', response.getRefreshToken());
     *     }
     * } catch (ResourceNotFoundException ex) {
     *     // Invalid refresh token → redirect to login
     * } catch (ValidationException ex) {
     *     // Expired or user inactive → redirect to login
     * }
     * }
     * </pre>
     * 
     * @param request Refresh token request containing the refresh token
     * @return RefreshTokenResponse with new access token (+ maybe new refresh token)
     * @throws ResourceNotFoundException if refresh token not found in database
     * @throws ValidationException if refresh token expired or user inactive
     */
    @Transactional
    public RefreshTokenResponse refresh(RefreshTokenRequest request) {
        log.info("Token refresh attempt with token: {}...", 
                 request.getRefreshToken().substring(0, Math.min(8, request.getRefreshToken().length())));

        // 1. Validate refresh token
        RefreshToken refreshToken = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        User user = refreshToken.getUser();
        log.debug("Refresh token validated for user: {} (ID: {})", user.getEmail(), user.getId());

        // 2. Generate new access token
        String newAccessToken = jwtTokenProvider.generateTokenForUser(user);
        log.debug("New access token generated for user: {}", user.getEmail());

        // 3. Build response
        RefreshTokenResponse.RefreshTokenResponseBuilder responseBuilder = RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationInSeconds())
                .issuedAt(LocalDateTime.now());

        // 4. Check if refresh token needs rotation (< 1 day remaining)
        if (refreshToken.needsRotation()) {
            log.info("Rotating refresh token for user: {} (only {} days remaining)", 
                     user.getEmail(), refreshToken.getDaysUntilExpiry());
            
            // Create new refresh token and delete old one
            RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(refreshToken);
            responseBuilder.refreshToken(newRefreshToken.getToken());
            
            log.debug("Refresh token rotated for user: {}", user.getEmail());
        }

        RefreshTokenResponse response = responseBuilder.build();
        
        log.info("Token refresh successful for user: {}", user.getEmail());
        return response;
    }

    // ==================== LOGOUT ====================

    /**
     * Logs out user by deleting refresh token (single device).
     * 
     * This method implements logout for a single device by deleting
     * the specific refresh token sent by the client.
     * 
     * Logout Flow:
     * <pre>
     * {@code
     * 1. Client sends logout request:
     *    POST /api/auth/logout
     *    { "refreshToken": "550e8400-..." }
     * 
     * 2. Delete refresh token from database:
     *    DELETE FROM refresh_tokens WHERE token = '550e8400-...'
     * 
     * 3. Token immediately invalid:
     *    - Cannot be used to refresh access token
     *    - User must login again on this device
     * 
     * 4. Access token still valid until expiry:
     *    - Remains valid for up to 15 minutes
     *    - Cannot be revoked (JWT is stateless)
     *    - For immediate revocation, implement token blacklist
     * 
     * 5. Client clears local storage:
     *    localStorage.removeItem('accessToken');
     *    localStorage.removeItem('refreshToken');
     * }
     * </pre>
     * 
     * Single Device Logout:
     * - Only deletes the specific refresh token sent
     * - User's other devices remain logged in
     * - Each device has unique refresh token
     * 
     * Example Scenario:
     * User has 3 devices:
     * - iPhone: refresh token A
     * - Laptop: refresh token B
     * - iPad: refresh token C
     * 
     * User logs out from iPhone:
     * - Token A deleted
     * - Tokens B and C still valid
     * - iPhone needs re-login, Laptop and iPad continue
     * 
     * Access Token Consideration:
     * After logout, access token remains valid until expiry (max 15 min).
     * This is acceptable because:
     * - Short expiration (15 min) limits risk
     * - User expects some delay in logout effect
     * - Implementing token blacklist adds complexity
     * 
     * For immediate revocation, implement token blacklist:
     * 1. Store revoked tokens in Redis
     * 2. Check blacklist in JWT filter
     * 3. Tokens expire from blacklist after 15 min
     * 
     * Example Usage:
     * <pre>
     * {@code
     * // In AuthController
     * @PostMapping("/logout")
     * public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
     *     authenticationService.logout(request.getRefreshToken());
     *     return ResponseEntity.noContent().build();
     * }
     * 
     * // Client-side
     * async function logout() {
     *     const refreshToken = localStorage.getItem('refreshToken');
     *     
     *     await fetch('/api/auth/logout', {
     *         method: 'POST',
     *         body: JSON.stringify({ refreshToken })
     *     });
     *     
     *     // Clear tokens
     *     localStorage.removeItem('accessToken');
     *     localStorage.removeItem('refreshToken');
     *     
     *     // Redirect to login
     *     window.location.href = '/login';
     * }
     * }
     * </pre>
     * 
     * @param refreshToken The refresh token to invalidate
     */
    @Transactional
    public void logout(String refreshToken) {
        log.info("Logout attempt with token: {}...", 
                 refreshToken.substring(0, Math.min(8, refreshToken.length())));

        // Delete refresh token from database
        int deleted = refreshTokenService.deleteByToken(refreshToken);

        if (deleted > 0) {
            log.info("Logout successful: Refresh token deleted");
        } else {
            log.warn("Logout: Refresh token not found (may have already been deleted)");
        }
    }

    /**
     * Logs out user from all devices by deleting all refresh tokens.
     * 
     * This method implements "logout from all devices" functionality.
     * All refresh tokens for the user are deleted, requiring re-login everywhere.
     * 
     * Use Cases:
     * - User clicks "Logout from all devices" button
     * - Security response to potential account compromise
     * - Password change (force re-login everywhere)
     * - Account suspension
     * 
     * Example:
     * <pre>
     * {@code
     * // In AuthController
     * @PostMapping("/logout-all")
     * public ResponseEntity<Void> logoutAllDevices(@AuthenticationPrincipal User user) {
     *     authenticationService.logoutAllDevices(user);
     *     return ResponseEntity.noContent().build();
     * }
     * }
     * </pre>
     * 
     * @param user User entity whose tokens to delete
     */
    @Transactional
    public void logoutAllDevices(User user) {
        log.info("Logout from all devices for user: {} (ID: {})", user.getEmail(), user.getId());

        int deleted = refreshTokenService.deleteAllByUser(user);

        log.info("Logout from all devices successful: {} refresh token(s) deleted for user: {}", 
                 deleted, user.getEmail());
    }

    // ==================== UTILITY ====================

    /**
     * Gets the currently authenticated user from security context.
     * 
     * This method retrieves the User entity for the currently authenticated user.
     * It extracts the email from SecurityContext and loads the user from database.
     * 
     * Usage in Controllers:
     * <pre>
     * {@code
     * @GetMapping("/me")
     * public ResponseEntity<UserResponse> getCurrentUser() {
     *     User user = authenticationService.getCurrentUser();
     *     UserResponse response = userMapper.toResponse(user);
     *     return ResponseEntity.ok(response);
     * }
     * }
     * </pre>
     * 
     * Note: This requires user to be authenticated (JWT valid).
     * If called without authentication, it will throw exception.
     * 
     * @return Current authenticated User entity
     * @throws ValidationException if user not authenticated or not found
     */
    @Transactional(readOnly = true)
    public User getCurrentUser() {
        // Get authentication from SecurityContext
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
            .getContext()
            .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ValidationException("User not authenticated", "NOT_AUTHENTICATED");
        }

        String email = authentication.getName();
        log.debug("Getting current user: {}", email);

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ValidationException("User not found", "USER_NOT_FOUND"));
    }
}

