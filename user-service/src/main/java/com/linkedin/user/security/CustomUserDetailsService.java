package com.linkedin.user.security;

import com.linkedin.user.model.User;
import com.linkedin.user.model.UserRole;
import com.linkedin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom implementation of Spring Security's UserDetailsService.
 * 
 * Purpose:
 * This class is the bridge between our User entity and Spring Security's authentication system.
 * Spring Security requires UserDetails interface, but we have User entity.
 * This service converts our User to UserDetails that Spring Security understands.
 * 
 * What is UserDetailsService?
 * 
 * UserDetailsService is a core interface in Spring Security that:
 * - Loads user-specific data during authentication
 * - Called by AuthenticationManager when user tries to login
 * - Returns UserDetails object that contains:
 *   - Username (email in our case)
 *   - Password (for verification)
 *   - Authorities (roles/permissions)
 *   - Account status (enabled, locked, expired, etc.)
 * 
 * Authentication Flow with UserDetailsService:
 * 
 * <pre>
 * {@code
 * 1. User submits login form:
 *    POST /api/auth/login
 *    { "email": "john@example.com", "password": "Pass123" }
 * 
 * 2. Spring Security's AuthenticationManager receives request
 * 
 * 3. AuthenticationManager calls:
 *    UserDetails userDetails = userDetailsService.loadUserByUsername("john@example.com")
 * 
 * 4. Our CustomUserDetailsService:
 *    - Queries database: userRepository.findByEmail("john@example.com")
 *    - Finds User entity
 *    - Converts to UserDetails
 *    - Returns to AuthenticationManager
 * 
 * 5. AuthenticationManager verifies password:
 *    passwordEncoder.matches("Pass123", userDetails.getPassword())
 *    - If match → Authentication successful
 *    - If no match → BadCredentialsException
 * 
 * 6. If successful:
 *    - User is authenticated
 *    - SecurityContext is populated
 *    - JWT token is generated
 *    - User can access protected endpoints
 * }
 * </pre>
 * 
 * Why Custom Implementation?
 * 
 * Spring Security provides default UserDetailsService implementations, but:
 * - Default: Uses in-memory users (not database)
 * - JdbcUserDetailsService: Uses specific table structure
 * - We have custom User entity with custom fields
 * - We need to map our UserRole enum to GrantedAuthority
 * - We have custom account status logic (isActive, emailVerified)
 * 
 * Custom UserDetails Implementation:
 * 
 * We create an anonymous UserDetails implementation that wraps our User entity.
 * This allows Spring Security to work with our User model without modifications.
 * 
 * Key Mappings:
 * - username → user.getEmail()
 * - password → user.getPassword() (BCrypt hash)
 * - authorities → user.getRoles() → Set<GrantedAuthority>
 * - enabled → user.isActive()
 * - accountNonLocked → true (we don't lock accounts yet)
 * - accountNonExpired → true (accounts don't expire)
 * - credentialsNonExpired → true (passwords don't expire)
 * 
 * Security Features:
 * 
 * 1. Account Status Checks:
 *    - isEnabled(): Checks if account is active
 *    - isAccountNonLocked(): Could check login attempt failures
 *    - isAccountNonExpired(): Could check subscription expiration
 *    - isCredentialsNonExpired(): Could force password rotation
 * 
 * 2. Role-Based Authorization:
 *    - Maps UserRole enum to GrantedAuthority
 *    - Spring Security uses these for @PreAuthorize checks
 *    - Example: @PreAuthorize("hasRole('ADMIN')")
 * 
 * 3. Lazy Loading Prevention:
 *    - Eager fetch roles with @Transactional(readOnly = true)
 *    - Prevents LazyInitializationException
 *    - Roles are needed immediately for authentication
 * 
 * Usage in Spring Security Configuration:
 * 
 * <pre>
 * {@code
 * @Configuration
 * public class SecurityConfig {
 * 
 *     @Autowired
 *     private CustomUserDetailsService userDetailsService;
 * 
 *     @Bean
 *     public AuthenticationManager authenticationManager(
 *         AuthenticationConfiguration config
 *     ) throws Exception {
 *         return config.getAuthenticationManager();
 *     }
 * 
 *     @Bean
 *     public DaoAuthenticationProvider authenticationProvider() {
 *         DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
 *         provider.setUserDetailsService(userDetailsService);  // ← Our custom service
 *         provider.setPasswordEncoder(passwordEncoder());
 *         return provider;
 *     }
 * }
 * }
 * </pre>
 * 
 * Testing Example:
 * 
 * <pre>
 * {@code
 * @Test
 * public void testLoadUserByUsername() {
 *     // Given: User exists in database
 *     User user = User.builder()
 *         .email("john@example.com")
 *         .password("$2a$10$...")  // BCrypt hash
 *         .roles(Set.of(UserRole.USER))
 *         .isActive(true)
 *         .build();
 *     userRepository.save(user);
 * 
 *     // When: Load user by username
 *     UserDetails userDetails = userDetailsService.loadUserByUsername("john@example.com");
 * 
 *     // Then: Verify mapping
 *     assertEquals("john@example.com", userDetails.getUsername());
 *     assertTrue(userDetails.isEnabled());
 *     assertTrue(userDetails.getAuthorities().stream()
 *         .anyMatch(auth -> auth.getAuthority().equals("ROLE_USER")));
 * }
 * }
 * </pre>
 * 
 * Common Issues and Solutions:
 * 
 * 1. LazyInitializationException:
 *    - Problem: Roles not loaded when Hibernate session closes
 *    - Solution: @Transactional(readOnly = true) on loadUserByUsername
 *    - Or: Use @ElementCollection(fetch = FetchType.EAGER) on User.roles
 * 
 * 2. UsernameNotFoundException:
 *    - Problem: User not found in database
 *    - Solution: Clear error message, log for monitoring
 *    - Return: HTTP 401 Unauthorized
 * 
 * 3. Role Prefix:
 *    - Spring Security requires "ROLE_" prefix
 *    - UserRole.USER → "ROLE_USER"
 *    - UserRole.ADMIN → "ROLE_ADMIN"
 *    - Use UserRole.getAuthority() for correct prefix
 * 
 * @see UserDetailsService
 * @see UserDetails
 * @see org.springframework.security.authentication.AuthenticationManager
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads user by username (email) for Spring Security authentication.
     * 
     * This is the core method that Spring Security calls during authentication.
     * It's called by AuthenticationManager when user tries to login.
     * 
     * Process:
     * 1. Receive username (email in our case)
     * 2. Query database for user
     * 3. If not found → throw UsernameNotFoundException
     * 4. If found → convert User entity to UserDetails
     * 5. Return UserDetails to Spring Security
     * 
     * Method Name Note:
     * - Spring Security interface uses "username" terminology
     * - In our system, we use email as username
     * - So loadUserByUsername("john@example.com") loads user by email
     * 
     * UserDetails Implementation:
     * We return an anonymous implementation of UserDetails that wraps our User entity.
     * This allows Spring Security to access user information without modifying User entity.
     * 
     * Returned UserDetails provides:
     * - getUsername() → user's email
     * - getPassword() → BCrypt hashed password
     * - getAuthorities() → user's roles as GrantedAuthority
     * - isEnabled() → whether account is active
     * - isAccountNonLocked() → account lock status
     * - isAccountNonExpired() → account expiration status
     * - isCredentialsNonExpired() → password expiration status
     * 
     * Example Flow:
     * <pre>
     * {@code
     * // User submits login
     * POST /api/auth/login
     * { "email": "john@example.com", "password": "Pass123" }
     * 
     * // Spring Security calls this method
     * UserDetails userDetails = loadUserByUsername("john@example.com");
     * 
     * // Spring Security verifies password
     * boolean matches = passwordEncoder.matches("Pass123", userDetails.getPassword());
     * 
     * // If matches:
     * // - User authenticated
     * // - SecurityContext populated
     * // - JWT token generated
     * }
     * </pre>
     * 
     * Security Checks:
     * 1. User exists? → If no, throw UsernameNotFoundException
     * 2. Account active? → Check isEnabled()
     * 3. Password matches? → Done by AuthenticationManager
     * 4. Roles assigned? → Used for authorization
     * 
     * Error Scenarios:
     * 
     * 1. User Not Found:
     *    - Throw UsernameNotFoundException
     *    - Spring Security converts to 401 Unauthorized
     *    - Generic message "Bad credentials" (security best practice)
     * 
     * 2. Account Disabled:
     *    - isEnabled() returns false
     *    - Spring Security throws DisabledException
     *    - Can return custom message: "Account is disabled"
     * 
     * 3. Account Locked:
     *    - isAccountNonLocked() returns false
     *    - Spring Security throws LockedException
     *    - Can return custom message: "Account is locked"
     * 
     * @param username User's email address (we use email as username)
     * @return UserDetails object containing user information for authentication
     * @throws UsernameNotFoundException if user with given email not found
     */
    @Override
    @Transactional(readOnly = true)  // Ensure roles are loaded (prevent LazyInitializationException)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username (email): {}", username);

        // Find user by email
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", username);
                    return new UsernameNotFoundException(
                        "User not found with email: " + username
                    );
                });

        log.debug("User found: {} (ID: {}), roles: {}", 
                  user.getEmail(), user.getId(), user.getRoles());

        // Convert our User entity to Spring Security's UserDetails
        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),                    // username
            user.getPassword(),                 // password (BCrypt hash)
            user.isAccountActive(),             // enabled (account active?)
            true,                               // accountNonExpired (we don't expire accounts)
            true,                               // credentialsNonExpired (we don't expire passwords)
            true,                               // accountNonLocked (we don't lock accounts yet)
            mapRolesToAuthorities(user.getRoles()) // authorities (roles)
        );
    }

    /**
     * Converts our UserRole enum to Spring Security's GrantedAuthority.
     * 
     * Spring Security's authorization system uses GrantedAuthority interface.
     * Our User entity has Set<UserRole>, but Spring Security needs Collection<GrantedAuthority>.
     * This method performs the conversion.
     * 
     * Role Mapping:
     * - UserRole.USER → SimpleGrantedAuthority("ROLE_USER")
     * - UserRole.ADMIN → SimpleGrantedAuthority("ROLE_ADMIN")
     * 
     * Role Prefix "ROLE_":
     * Spring Security requires "ROLE_" prefix for @PreAuthorize checks.
     * - With prefix: @PreAuthorize("hasRole('USER')") → checks "ROLE_USER"
     * - Without prefix: @PreAuthorize("hasAuthority('USER')") → checks "USER"
     * 
     * Our UserRole.getAuthority() automatically adds "ROLE_" prefix.
     * 
     * Example:
     * <pre>
     * {@code
     * // User has roles: [USER, ADMIN]
     * Set<UserRole> userRoles = Set.of(UserRole.USER, UserRole.ADMIN);
     * 
     * // Convert to authorities
     * Collection<GrantedAuthority> authorities = mapRolesToAuthorities(userRoles);
     * 
     * // Result:
     * // [
     * //   SimpleGrantedAuthority("ROLE_USER"),
     * //   SimpleGrantedAuthority("ROLE_ADMIN")
     * // ]
     * 
     * // Now Spring Security can check:
     * // @PreAuthorize("hasRole('USER')") → true
     * // @PreAuthorize("hasRole('ADMIN')") → true
     * // @PreAuthorize("hasRole('MODERATOR')") → false
     * }
     * </pre>
     * 
     * Usage in Authorization:
     * 
     * 1. Method Security:
     * <pre>
     * {@code
     * @PreAuthorize("hasRole('ADMIN')")
     * public void deleteUser(Long id) {
     *     // Only users with ADMIN role can access
     * }
     * }
     * </pre>
     * 
     * 2. Multiple Roles:
     * <pre>
     * {@code
     * @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
     * public void viewProfile(Long id) {
     *     // Users with USER or ADMIN role can access
     * }
     * }
     * </pre>
     * 
     * 3. Complex Expressions:
     * <pre>
     * {@code
     * @PreAuthorize("hasRole('USER') and #id == authentication.principal.id")
     * public void updateProfile(Long id) {
     *     // User can only update their own profile
     * }
     * }
     * </pre>
     * 
     * Why Stream API?
     * - Clean, functional approach
     * - Handles empty sets gracefully
     * - Returns immutable collection (via Collectors.toSet())
     * - Type-safe transformation
     * 
     * Null Safety:
     * - If roles is null, stream returns empty collection
     * - Spring Security handles empty authorities collection
     * - User has no special permissions (like anonymous user)
     * 
     * @param roles Set of UserRole enums from User entity
     * @return Collection of GrantedAuthority for Spring Security
     */
    private Collection<? extends GrantedAuthority> mapRolesToAuthorities(Set<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            log.warn("User has no roles assigned, returning empty authorities");
            return Set.of();
        }

        log.debug("Mapping {} role(s) to authorities", roles.size());

        return roles.stream()
                .map(role -> {
                    // UserRole.getAuthority() returns "ROLE_USER" or "ROLE_ADMIN"
                    String authority = role.getAuthority();
                    log.debug("Mapping role {} to authority {}", role, authority);
                    return new SimpleGrantedAuthority(authority);
                })
                .collect(Collectors.toSet());
    }

    /**
     * Loads user by ID.
     * 
     * This is a convenience method (not part of UserDetailsService interface).
     * Useful when you have user ID from JWT token and want to reload user.
     * 
     * Use Cases:
     * - JWT contains user ID
     * - Need to reload user from database
     * - Check if user account is still active
     * - Get updated roles (in case they changed)
     * 
     * Example:
     * <pre>
     * {@code
     * // Extract user ID from JWT
     * Long userId = jwtTokenProvider.getUserIdFromToken(token);
     * 
     * // Reload user from database
     * UserDetails userDetails = userDetailsService.loadUserById(userId);
     * 
     * // Use for authorization checks
     * if (userDetails.getAuthorities().stream()
     *     .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"))) {
     *     // User is admin
     * }
     * }
     * </pre>
     * 
     * @param userId User's database ID
     * @return UserDetails object for the user
     * @throws UsernameNotFoundException if user with given ID not found
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) {
        log.debug("Loading user by ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found with ID: {}", userId);
                    return new UsernameNotFoundException("User not found with ID: " + userId);
                });

        log.debug("User found: {} (ID: {})", user.getEmail(), user.getId());

        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPassword(),
            user.isAccountActive(),
            true,
            true,
            true,
            mapRolesToAuthorities(user.getRoles())
        );
    }

    /**
     * Gets the User entity from UserDetails (if it's our custom implementation).
     * 
     * This is useful in controllers when you have UserDetails from SecurityContext
     * but need the full User entity.
     * 
     * However, best practice is to reload user from database when you need full entity:
     * <pre>
     * {@code
     * // In controller
     * @GetMapping("/profile")
     * public UserResponse getProfile(@AuthenticationPrincipal UserDetails userDetails) {
     *     // Option 1: Reload from database (recommended)
     *     User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
     *     
     *     // Option 2: If UserDetails is just used for email
     *     String email = userDetails.getUsername();
     * }
     * }
     * </pre>
     * 
     * Note: This method is not commonly used because UserDetails is typically
     * only used for authentication/authorization, not for accessing full entity.
     * 
     * @param userDetails Spring Security UserDetails object
     * @return User entity if userDetails contains email, otherwise null
     */
    public User getUserFromUserDetails(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }

        String email = userDetails.getUsername();
        log.debug("Getting User entity for UserDetails with email: {}", email);

        return userRepository.findByEmail(email).orElse(null);
    }
}

