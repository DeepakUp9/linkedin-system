package com.linkedin.user.service;

import com.linkedin.common.exceptions.BusinessException;
import com.linkedin.common.exceptions.ResourceNotFoundException;
import com.linkedin.common.exceptions.ValidationException;
import com.linkedin.user.dto.CreateUserRequest;
import com.linkedin.user.dto.UpdateUserRequest;
import com.linkedin.user.dto.UserResponse;
import com.linkedin.user.mapper.UserMapper;
import com.linkedin.user.model.AccountType;
import com.linkedin.user.model.User;
import com.linkedin.user.patterns.factory.UserFactory;
import com.linkedin.user.patterns.strategy.ProfileValidationStrategy;
import com.linkedin.user.patterns.strategy.ValidationStrategyFactory;
import com.linkedin.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service layer for User management.
 * 
 * This is the core business logic layer that orchestrates all user-related operations.
 * It acts as a facade, coordinating between multiple components to fulfill business requirements.
 * 
 * Design Principle: Service Layer Pattern
 * 
 * What is the Service Layer?
 * 
 * The Service Layer is an architectural pattern that defines an application's boundary
 * and encapsulates business logic. It sits between the presentation layer (controllers)
 * and the data access layer (repositories).
 * 
 * Layered Architecture:
 * 
 * <pre>
 * {@code
 * ┌──────────────────────────────────────┐
 * │   Presentation Layer (Controllers)   │  ← REST API, accepts HTTP requests
 * │   - Handles HTTP requests/responses  │
 * │   - Validates input (@Valid)         │
 * │   - Returns DTOs wrapped in ApiResponse
 * └──────────────┬───────────────────────┘
 *                │
 *                ▼
 * ┌──────────────────────────────────────┐
 * │     Service Layer (UserService)      │  ← Business Logic (THIS CLASS)
 * │   - Orchestrates business operations │
 * │   - Transaction management           │
 * │   - Uses factories and strategies    │
 * │   - Maps entities ↔ DTOs            │
 * └──────────────┬───────────────────────┘
 *                │
 *                ▼
 * ┌──────────────────────────────────────┐
 * │  Data Access Layer (Repository)      │  ← Database Operations
 * │   - CRUD operations                  │
 * │   - Custom queries                   │
 * │   - Pagination/sorting               │
 * └──────────────────────────────────────┘
 * }
 * </pre>
 * 
 * Responsibilities of This Service:
 * 
 * 1. Business Logic:
 *    - User creation with account type-specific rules
 *    - Profile updates with validation
 *    - Account management (activate/deactivate)
 *    - Premium upgrades
 * 
 * 2. Orchestration:
 *    - Coordinates UserFactory, ValidationStrategyFactory, UserRepository, UserMapper
 *    - Ensures correct sequence of operations
 *    - Maintains data consistency
 * 
 * 3. Transaction Management:
 *    - @Transactional on write operations
 *    - Ensures atomicity (all-or-nothing)
 *    - Rollback on exceptions
 * 
 * 4. Exception Handling:
 *    - Translates low-level exceptions to business exceptions
 *    - Provides meaningful error messages
 *    - Maintains API contract
 * 
 * 5. Data Transformation:
 *    - Converts entities to DTOs (via UserMapper)
 *    - Never exposes entities to controllers
 *    - Maintains clean separation of concerns
 * 
 * Design Patterns Used:
 * 
 * 1. Factory Pattern (UserFactory):
 *    - Creates User entities with proper validation
 *    - Encapsulates creation logic
 *    - Handles password hashing
 * 
 * 2. Strategy Pattern (ValidationStrategyFactory):
 *    - Selects appropriate validation strategy
 *    - BASIC vs PREMIUM validation rules
 *    - Extensible for new account types
 * 
 * 3. Repository Pattern (UserRepository):
 *    - Abstracts database operations
 *    - Provides query methods
 *    - Managed by Spring Data JPA
 * 
 * 4. DTO Pattern (UserResponse, CreateUserRequest, UpdateUserRequest):
 *    - Separates API contract from domain model
 *    - Security (no password exposure)
 *    - Flexibility (API versioning)
 * 
 * Transaction Management:
 * 
 * @Transactional Annotation:
 * - Applied to all write methods (create, update, delete)
 * - Read-only transactions for read methods (optimization)
 * - Automatic rollback on runtime exceptions
 * - Ensures database consistency
 * 
 * Example Transaction Flow:
 * <pre>
 * {@code
 * @Transactional  // ← Spring starts database transaction
 * public UserResponse createUser(CreateUserRequest request) {
 *     // 1. Validate email uniqueness (DB query)
 *     // 2. Create user entity
 *     // 3. Validate profile
 *     // 4. Save to database
 *     // 5. Map to DTO
 *     // 6. Spring commits transaction (if no exception)
 *     //    OR rollbacks (if exception thrown)
 * }
 * }
 * </pre>
 * 
 * Exception Strategy:
 * 
 * This service throws domain-specific exceptions:
 * - ValidationException: Input validation failures (HTTP 400)
 * - ResourceNotFoundException: Entity not found (HTTP 404)
 * - BusinessException: Business rule violations (HTTP 422)
 * 
 * These are caught by GlobalExceptionHandler and converted to appropriate HTTP responses.
 * 
 * Why This Matters:
 * - Service doesn't know about HTTP (clean separation)
 * - Easy to test (no HTTP dependencies)
 * - Reusable in different contexts (REST, GraphQL, gRPC)
 * 
 * Testing Strategy:
 * 
 * Unit Tests:
 * - Mock all dependencies (repository, factory, mapper)
 * - Test business logic in isolation
 * - Verify exception handling
 * 
 * Integration Tests:
 * - Use real database (Testcontainers)
 * - Verify transaction behavior
 * - Test end-to-end flows
 * 
 * Thread Safety:
 * - Service is a singleton (@Service)
 * - All dependencies are stateless
 * - No shared mutable state
 * - Safe for concurrent requests
 * 
 * Performance Considerations:
 * 
 * 1. N+1 Query Prevention:
 *    - Use @EntityGraph or JOIN FETCH when loading relationships
 *    - Careful with lazy loading
 * 
 * 2. Pagination:
 *    - Use Pageable for large result sets
 *    - Don't load all records at once
 * 
 * 3. Caching:
 *    - Consider @Cacheable for frequently accessed data
 *    - User profiles are good candidates
 * 
 * 4. Batch Operations:
 *    - Use saveAll() for multiple inserts
 *    - Consider bulk updates for mass operations
 * 
 * @see UserFactory
 * @see ValidationStrategyFactory
 * @see UserRepository
 * @see UserMapper
 * @see User
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)  // Default: read-only transactions (optimization)
public class UserService {

    private final UserRepository userRepository;
    private final UserFactory userFactory;
    private final ValidationStrategyFactory validationStrategyFactory;
    private final UserMapper userMapper;

    /**
     * Creates a new user with BASIC account type.
     * 
     * Process Flow:
     * 1. Check if email already exists (uniqueness constraint)
     * 2. Create User entity using UserFactory (password hashing included)
     * 3. Validate profile using appropriate strategy (BASIC validation)
     * 4. Save to database (transaction)
     * 5. Map to UserResponse DTO
     * 6. Return DTO to controller
     * 
     * Business Rules:
     * - Email must be unique across all users
     * - Password must meet strength requirements (handled by factory)
     * - BASIC users: Only email, password, name required
     * 
     * Transaction:
     * - @Transactional (write operation)
     * - Commits if successful
     * - Rollbacks if any exception thrown
     * 
     * Example:
     * <pre>
     * {@code
     * CreateUserRequest request = CreateUserRequest.builder()
     *     .email("john@example.com")
     *     .password("SecurePass123")
     *     .name("John Doe")
     *     .build();
     * 
     * UserResponse response = userService.createUser(request);
     * // response.id = 1
     * // response.email = "john@example.com"
     * // response.accountType = BASIC
     * }
     * </pre>
     * 
     * @param request CreateUserRequest containing user data
     * @return UserResponse DTO with created user data (NO password!)
     * @throws ValidationException if email already exists or validation fails
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Creating new user with email: {}", request.getEmail());
        
        // Step 1: Check email uniqueness
        validateEmailUniqueness(request.getEmail());
        
        // Step 2: Create user entity (Factory Pattern)
        // Factory handles password hashing, default values
        User user = userFactory.createUser(request);
        log.debug("User entity created: {}", user.getEmail());
        
        // Step 3: Validate profile (Strategy Pattern)
        // Selects BASIC validation strategy
        ProfileValidationStrategy validationStrategy = 
            validationStrategyFactory.getStrategy(user.getAccountType());
        validationStrategy.validate(user);
        log.debug("User validation passed for: {}", user.getEmail());
        
        // Step 4: Save to database
        User savedUser = userRepository.save(user);
        log.info("User created successfully with ID: {}", savedUser.getId());
        
        // Step 5: Map to DTO (MapStruct)
        // Removes sensitive fields (password, audit fields)
        return userMapper.toResponse(savedUser);
    }

    /**
     * Creates a new user with PREMIUM account type.
     * 
     * Similar to createUser() but with stricter validation requirements.
     * 
     * Business Rules:
     * - All BASIC requirements
     * - PREMIUM users MUST provide:
     *   * Headline (min 10 characters)
     *   * Summary (min 50 characters)
     *   * Location
     * 
     * Use Case:
     * - Direct premium signups (paid registration)
     * - Special promotional accounts
     * - Enterprise/business accounts
     * 
     * @param request CreateUserRequest with premium-required fields
     * @return UserResponse with accountType = PREMIUM
     * @throws ValidationException if premium requirements not met
     */
    @Transactional
    public UserResponse createPremiumUser(CreateUserRequest request) {
        log.info("Creating new PREMIUM user with email: {}", request.getEmail());
        
        // Step 1: Check email uniqueness
        validateEmailUniqueness(request.getEmail());
        
        // Step 2: Create premium user (Factory Pattern)
        // Factory validates premium-specific requirements
        User user = userFactory.createPremiumUser(request);
        log.debug("Premium user entity created: {}", user.getEmail());
        
        // Step 3: Validate with PREMIUM strategy
        // Stricter validation (headline, summary, location required)
        ProfileValidationStrategy validationStrategy = 
            validationStrategyFactory.getStrategy(user.getAccountType());
        validationStrategy.validate(user);
        log.debug("Premium user validation passed for: {}", user.getEmail());
        
        // Step 4: Save to database
        User savedUser = userRepository.save(user);
        log.info("Premium user created successfully with ID: {}", savedUser.getId());
        
        // Step 5: Map to DTO
        return userMapper.toResponse(savedUser);
    }

    /**
     * Retrieves a user by ID.
     * 
     * Read Operation:
     * - Uses @Transactional(readOnly = true) from class level
     * - Read-only transactions are optimized (no flush, no dirty checking)
     * 
     * @param id User ID
     * @return UserResponse DTO
     * @throws ResourceNotFoundException if user not found
     */
    public UserResponse getUserById(Long id) {
        log.debug("Fetching user by ID: {}", id);
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("User not found with ID: {}", id);
                return new ResourceNotFoundException("User", "id", id);
            });
        
        log.debug("User found: {}", user.getEmail());
        return userMapper.toResponse(user);
    }

    /**
     * Retrieves a user by email.
     * 
     * Use Cases:
     * - Login lookup
     * - Profile search
     * - Email verification
     * 
     * @param email User email
     * @return UserResponse DTO
     * @throws ResourceNotFoundException if user not found
     */
    public UserResponse getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> {
                log.warn("User not found with email: {}", email);
                return new ResourceNotFoundException("User", "email", email);
            });
        
        log.debug("User found with ID: {}", user.getId());
        return userMapper.toResponse(user);
    }

    /**
     * Retrieves all users (with pagination support).
     * 
     * Pagination:
     * - Avoids loading all records at once
     * - Supports sorting
     * - Example: GET /api/users?page=0&size=20&sort=createdAt,desc
     * 
     * Performance:
     * - Only loads requested page from database
     * - Count query for total elements
     * 
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of UserResponse DTOs
     */
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        log.debug("Fetching users with pagination: {}", pageable);
        
        Page<User> userPage = userRepository.findAll(pageable);
        log.debug("Found {} users on page {} of {}", 
            userPage.getNumberOfElements(), 
            userPage.getNumber(), 
            userPage.getTotalPages());
        
        // Map each User entity to UserResponse DTO
        return userPage.map(userMapper::toResponse);
    }

    /**
     * Retrieves all active users.
     * 
     * Business Rule:
     * - Only returns users where isActive = true
     * - Excludes suspended/deleted accounts
     * 
     * @return List of active users
     */
    public List<UserResponse> getActiveUsers() {
        log.debug("Fetching all active users");
        
        List<User> activeUsers = userRepository.findByIsActiveTrue();
        log.debug("Found {} active users", activeUsers.size());
        
        return userMapper.toResponseList(activeUsers);
    }

    /**
     * Retrieves users by account type.
     * 
     * Use Cases:
     * - Analytics (count premium vs basic users)
     * - Targeted notifications
     * - Feature rollout to specific tiers
     * 
     * @param accountType BASIC or PREMIUM
     * @param pageable Pagination parameters
     * @return Page of users with specified account type
     */
    public Page<UserResponse> getUsersByAccountType(AccountType accountType, Pageable pageable) {
        log.debug("Fetching users with account type: {}", accountType);
        
        Page<User> userPage = userRepository.findByAccountType(accountType, pageable);
        log.debug("Found {} {} users", userPage.getTotalElements(), accountType);
        
        return userPage.map(userMapper::toResponse);
    }

    /**
     * Updates user profile (PATCH operation).
     * 
     * Process Flow:
     * 1. Fetch existing user from database
     * 2. Update only provided fields (null fields ignored)
     * 3. Re-validate profile with current account type strategy
     * 4. Save updated entity (JPA dirty checking updates only changed fields)
     * 5. Map to DTO and return
     * 
     * PATCH Semantics:
     * - Only updates fields present in request
     * - Null fields in request are IGNORED (not set to null)
     * - Preserves all other fields
     * 
     * Example:
     * <pre>
     * {@code
     * // Existing user:
     * User {id: 1, name: "John", headline: "Developer", location: "SF"}
     * 
     * // Update request (only changing name):
     * UpdateUserRequest {name: "Jane", headline: null, location: null}
     * 
     * // Result:
     * User {id: 1, name: "Jane", headline: "Developer", location: "SF"}
     * //            ↑ Updated    ↑ Preserved        ↑ Preserved
     * }
     * </pre>
     * 
     * @param id User ID
     * @param request UpdateUserRequest with fields to update
     * @return UserResponse with updated data
     * @throws ResourceNotFoundException if user not found
     * @throws ValidationException if updated profile doesn't meet requirements
     */
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        log.info("Updating user with ID: {}", id);
        
        // Step 1: Fetch existing user
        User user = userRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("User not found with ID: {}", id);
                return new ResourceNotFoundException("User", "id", id);
            });
        
        // Step 2: Update fields (MapStruct with NullValuePropertyMappingStrategy.IGNORE)
        // Only non-null fields in request are updated
        userMapper.updateFromDto(request, user);
        log.debug("User fields updated for: {}", user.getEmail());
        
        // Step 3: Re-validate profile
        // Important: If user is PREMIUM, updated profile must still meet PREMIUM requirements
        ProfileValidationStrategy validationStrategy = 
            validationStrategyFactory.getStrategy(user.getAccountType());
        validationStrategy.validate(user);
        log.debug("Updated user validation passed for: {}", user.getEmail());
        
        // Step 4: Save (JPA dirty checking detects changes)
        // Only modified columns updated in database
        User updatedUser = userRepository.save(user);
        log.info("User updated successfully with ID: {}", updatedUser.getId());
        
        // Step 5: Map to DTO
        return userMapper.toResponse(updatedUser);
    }

    /**
     * Deletes a user (soft delete - just deactivates).
     * 
     * Soft Delete vs Hard Delete:
     * 
     * Hard Delete (NOT used here):
     * - Permanently removes record from database
     * - Loss of historical data
     * - Breaks foreign key relationships
     * - Can't recover if deleted by mistake
     * 
     * Soft Delete (Used here):
     * - Sets isActive = false
     * - Preserves all data
     * - Maintains referential integrity
     * - Can reactivate if needed
     * - Audit trail preserved
     * 
     * Business Rules:
     * - User can't login after deactivation
     * - Profile hidden from search results
     * - Posts/connections preserved
     * - Can be reactivated by admin
     * 
     * @param id User ID to deactivate
     * @throws ResourceNotFoundException if user not found
     */
    @Transactional
    public void deleteUser(Long id) {
        log.info("Deactivating user with ID: {}", id);
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("User not found with ID: {}", id);
                return new ResourceNotFoundException("User", "id", id);
            });
        
        // Soft delete: Set isActive to false
        user.setIsActive(false);
        userRepository.save(user);
        
        log.info("User deactivated successfully: {}", user.getEmail());
    }

    /**
     * Upgrades a user from BASIC to PREMIUM.
     * 
     * Business Rules:
     * 1. User must currently be BASIC
     * 2. Profile must meet PREMIUM requirements:
     *    - Headline (min 10 chars)
     *    - Summary (min 50 chars)
     *    - Location
     * 3. If requirements not met, throw ValidationException with clear message
     * 
     * Process Flow:
     * 1. Fetch user
     * 2. Verify current account type is BASIC
     * 3. Validate profile meets PREMIUM requirements
     * 4. Upgrade accountType to PREMIUM
     * 5. Save and return
     * 
     * Use Case:
     * - User purchases premium subscription
     * - Promotional upgrade
     * - Admin grants premium access
     * 
     * @param id User ID to upgrade
     * @return UserResponse with accountType = PREMIUM
     * @throws ResourceNotFoundException if user not found
     * @throws BusinessException if user already premium
     * @throws ValidationException if profile doesn't meet premium requirements
     */
    @Transactional
    public UserResponse upgradeToPremium(Long id) {
        log.info("Upgrading user to PREMIUM: {}", id);
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("User not found with ID: {}", id);
                return new ResourceNotFoundException("User", "id", id);
            });
        
        // Verify user is currently BASIC
        if (user.isPremium()) {
            log.warn("User {} is already PREMIUM", id);
            throw new BusinessException(
                "User is already a PREMIUM member",
                "USER_ALREADY_PREMIUM",
                HttpStatus.CONFLICT
            );
        }
        
        // Validate profile meets PREMIUM requirements BEFORE upgrading
        ProfileValidationStrategy premiumStrategy = 
            validationStrategyFactory.getStrategy(AccountType.PREMIUM);
        
        try {
            premiumStrategy.validate(user);
        } catch (ValidationException e) {
            log.warn("User {} profile doesn't meet PREMIUM requirements: {}", id, e.getMessage());
            throw new ValidationException(
                "Profile must be complete before upgrading to PREMIUM. " +
                "Required: headline (10+ chars), summary (50+ chars), location. " +
                "Please update your profile first.",
                "PREMIUM_REQUIREMENTS_NOT_MET"
            );
        }
        
        // Upgrade to premium
        user.upgradeToPremium();
        User upgradedUser = userRepository.save(user);
        
        log.info("User {} upgraded to PREMIUM successfully", id);
        return userMapper.toResponse(upgradedUser);
    }

    /**
     * Searches for users by name, email, or headline.
     * 
     * Full-Text Search:
     * - Case-insensitive
     * - Searches across multiple fields (name, email, headline)
     * - Uses LIKE '%term%' (database-specific implementation)
     * 
     * Performance Note:
     * - LIKE with leading wildcard ('%term') can't use index
     * - Consider Elasticsearch for production full-text search
     * - Or use database-specific full-text search (PostgreSQL tsquery)
     * 
     * @param searchTerm Search string
     * @param accountType Filter by account type (optional)
     * @param pageable Pagination parameters
     * @return Page of matching users
     */
    public Page<UserResponse> searchUsers(
        String searchTerm, 
        AccountType accountType, 
        Pageable pageable
    ) {
        log.debug("Searching users with term: '{}', accountType: {}", searchTerm, accountType);
        
        Page<User> userPage = userRepository.searchActiveUsersByAccountType(
            searchTerm, 
            accountType, 
            pageable
        );
        
        log.debug("Found {} matching users", userPage.getTotalElements());
        return userPage.map(userMapper::toResponse);
    }

    /**
     * Gets recently created active users.
     * 
     * Business Use Case:
     * - "New members" section
     * - Welcome campaigns
     * - Onboarding analytics
     * 
     * @param since Date/time to search from
     * @return List of users created after specified date
     */
    public List<UserResponse> getRecentUsers(LocalDateTime since) {
        log.debug("Fetching users created after: {}", since);
        
        List<User> recentUsers = userRepository.findActiveUsersCreatedAfter(since);
        log.debug("Found {} recent users", recentUsers.size());
        
        return userMapper.toResponseList(recentUsers);
    }

    // ============================================
    // Private Helper Methods
    // ============================================

    /**
     * Validates that email doesn't already exist.
     * 
     * Business Rule:
     * - Email must be unique across all users
     * - Case-insensitive check (handled by database)
     * 
     * @param email Email to check
     * @throws ValidationException if email already exists
     */
    private void validateEmailUniqueness(String email) {
        if (userRepository.existsByEmail(email)) {
            log.warn("Email already exists: {}", email);
            throw new ValidationException(
                "Email address is already registered: " + email,
                "EMAIL_ALREADY_EXISTS"
            );
        }
    }
}

