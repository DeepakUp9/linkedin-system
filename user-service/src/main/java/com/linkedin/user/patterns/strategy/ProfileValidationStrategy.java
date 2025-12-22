package com.linkedin.user.patterns.strategy;

import com.linkedin.user.model.AccountType;
import com.linkedin.user.model.User;

/**
 * Strategy Pattern Interface for user profile validation.
 * 
 * This interface defines the contract for validating user profiles.
 * Different implementations apply different validation rules based on
 * the user's account type (BASIC vs PREMIUM).
 * 
 * Design Pattern: Strategy Pattern
 * 
 * Intent:
 * - Define a family of algorithms (validation rules)
 * - Encapsulate each algorithm in a separate class
 * - Make them interchangeable
 * - Let the algorithm vary independently from clients that use it
 * 
 * Components:
 * - ProfileValidationStrategy (this interface) - Strategy interface
 * - BasicProfileValidationStrategy - Concrete strategy for BASIC users
 * - PremiumProfileValidationStrategy - Concrete strategy for PREMIUM users
 * - UserFactory/UserService - Context that uses strategy
 * 
 * Why Strategy Pattern Here:
 * 
 * 1. Multiple Algorithms:
 *    - BASIC users: Minimal validation (email, password, name)
 *    - PREMIUM users: Strict validation (all basic + headline, summary, location)
 * 
 * 2. Eliminate Conditionals:
 *    Instead of: if (accountType == BASIC) { ... } else if (accountType == PREMIUM) { ... }
 *    We use: strategy.validate(user)  // Strategy knows what to validate
 * 
 * 3. Open/Closed Principle:
 *    - Open for extension: Add new strategy for ENTERPRISE users
 *    - Closed for modification: Don't change existing strategies
 * 
 * 4. Single Responsibility:
 *    - Each strategy class has ONE job: validate one account type
 * 
 * Real-World Analogy:
 * Think of airport security screening:
 * - Economy passengers: Basic security check
 * - Business class: Basic + additional screening
 * - First class: Basic + VIP fast-track procedures
 * Same process (security check) but different rules based on ticket type.
 * 
 * Benefits:
 * 1. Clean code: No complex if-else chains
 * 2. Testability: Test each strategy independently
 * 3. Maintainability: Change one strategy without affecting others
 * 4. Extensibility: Add new strategies easily (ENTERPRISE, TRIAL, etc.)
 * 5. Separation of concerns: Each strategy focuses on its own rules
 * 
 * Pattern Structure:
 * 
 * <pre>
 * {@code
 *                  ┌─────────────────────────────┐
 *                  │ ProfileValidationStrategy   │  ← Strategy Interface
 *                  │   + validate(User)          │
 *                  └──────────────┬──────────────┘
 *                                 │
 *                 ┌───────────────┴───────────────┐
 *                 │                               │
 *    ┌────────────▼──────────────┐   ┌───────────▼────────────────┐
 *    │ BasicProfileValidation    │   │ PremiumProfileValidation   │
 *    │ Strategy                  │   │ Strategy                   │
 *    │   + validate(User)        │   │   + validate(User)         │
 *    │   - validateBasicRules()  │   │   - validateBasicRules()   │
 *    │                           │   │   - validatePremiumRules() │
 *    └───────────────────────────┘   └────────────────────────────┘
 *                 ▲                               ▲
 *                 │                               │
 *                 └───────────────┬───────────────┘
 *                                 │
 *                    ┌────────────▼────────────┐
 *                    │ UserFactory/Service     │  ← Context
 *                    │   - strategy            │
 *                    │   + createUser()        │
 *                    │   + validateProfile()   │
 *                    └─────────────────────────┘
 * }
 * </pre>
 * 
 * Usage Example:
 * 
 * <pre>
 * {@code
 * @Service
 * public class UserService {
 *     @Autowired
 *     private BasicProfileValidationStrategy basicStrategy;
 *     
 *     @Autowired
 *     private PremiumProfileValidationStrategy premiumStrategy;
 *     
 *     public void validateUser(User user) {
 *         ProfileValidationStrategy strategy;
 *         
 *         // Select strategy based on account type
 *         if (user.getAccountType() == AccountType.PREMIUM) {
 *             strategy = premiumStrategy;
 *         } else {
 *             strategy = basicStrategy;
 *         }
 *         
 *         // Execute validation (polymorphism!)
 *         strategy.validate(user);
 *     }
 * }
 * }
 * </pre>
 * 
 * Alternative Usage with Map (More Scalable):
 * 
 * <pre>
 * {@code
 * @Service
 * public class UserService {
 *     private final Map<AccountType, ProfileValidationStrategy> strategies;
 *     
 *     public UserService(
 *         BasicProfileValidationStrategy basicStrategy,
 *         PremiumProfileValidationStrategy premiumStrategy
 *     ) {
 *         this.strategies = Map.of(
 *             AccountType.BASIC, basicStrategy,
 *             AccountType.PREMIUM, premiumStrategy
 *         );
 *     }
 *     
 *     public void validateUser(User user) {
 *         ProfileValidationStrategy strategy = strategies.get(user.getAccountType());
 *         strategy.validate(user);
 *     }
 * }
 * }
 * </pre>
 * 
 * Validation Rules by Account Type:
 * 
 * BASIC (Free Tier):
 * - Email: Required, valid format
 * - Password: Required, strong (8+ chars, upper, lower, digit)
 * - Name: Required, 2-255 characters
 * - Headline: Optional
 * - Summary: Optional
 * - Location: Optional
 * - Profile Photo: Optional
 * 
 * PREMIUM (Paid Tier):
 * - All BASIC requirements
 * - Headline: Required, min 10 characters
 * - Summary: Required, min 50 characters
 * - Location: Required
 * - Profile Photo: Strongly recommended
 * 
 * Future Extensions:
 * - TRIAL: Like BASIC but with expiration date
 * - ENTERPRISE: Like PREMIUM but with company verification
 * - STUDENT: Like BASIC but with .edu email requirement
 * 
 * @see BasicProfileValidationStrategy
 * @see PremiumProfileValidationStrategy
 * @see User
 */
public interface ProfileValidationStrategy {

    /**
     * Validates a user profile according to strategy-specific rules.
     * 
     * This is the main method that each concrete strategy must implement.
     * The method should throw ValidationException if the profile doesn't
     * meet the requirements for this account type.
     * 
     * Implementation Guidelines:
     * 1. Check all required fields for this account type
     * 2. Validate field formats (email, phone, etc.)
     * 3. Validate field lengths (min/max)
     * 4. Throw ValidationException with clear message if validation fails
     * 5. Return silently if validation passes
     * 
     * Example Implementation:
     * <pre>
     * {@code
     * @Override
     * public void validate(User user) {
     *     // Required fields
     *     if (user.getEmail() == null || user.getEmail().isEmpty()) {
     *         throw new ValidationException("Email is required", "USER_EMAIL_REQUIRED");
     *     }
     *     
     *     // Format validation
     *     if (!isValidEmailFormat(user.getEmail())) {
     *         throw new ValidationException("Invalid email format", "USER_EMAIL_INVALID");
     *     }
     *     
     *     // Length validation
     *     if (user.getName().length() < 2) {
     *         throw new ValidationException("Name too short", "USER_NAME_TOO_SHORT");
     *     }
     * }
     * }
     * </pre>
     * 
     * Validation Flow:
     * 1. Check required fields exist (not null)
     * 2. Validate formats (email regex, phone regex)
     * 3. Validate lengths (min/max characters)
     * 4. Validate business rules (age >= 18, etc.)
     * 5. If any check fails, throw ValidationException immediately
     * 6. If all checks pass, return (no exception = valid)
     * 
     * @param user User entity to validate
     * @throws ValidationException if validation fails
     *         - Contains error message for user
     *         - Contains error code for logging/tracking
     */
    void validate(User user);

    /**
     * Gets the account type this strategy validates.
     * 
     * Useful for:
     * - Logging which strategy is being used
     * - Building strategy maps (AccountType → Strategy)
     * - Debugging validation issues
     * 
     * Example:
     * <pre>
     * {@code
     * log.debug("Using {} strategy for user {}", 
     *     strategy.getAccountType(), 
     *     user.getEmail()
     * );
     * }
     * </pre>
     * 
     * @return AccountType this strategy is designed for
     */
    AccountType getAccountType();

    /**
     * Gets a human-readable description of validation rules.
     * 
     * Useful for:
     * - Showing users what's required for their account type
     * - API documentation
     * - Error messages ("Your profile doesn't meet PREMIUM requirements...")
     * 
     * Example return values:
     * - "BASIC: Email, password, and name required"
     * - "PREMIUM: All basic fields plus headline (10+ chars), summary (50+ chars), location"
     * 
     * @return Description of validation rules for this strategy
     */
    String getValidationDescription();
}

