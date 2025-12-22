package com.linkedin.user.patterns.factory;

import com.linkedin.user.dto.CreateUserRequest;
import com.linkedin.user.dto.UpdateUserRequest;
import com.linkedin.user.model.User;

/**
 * Factory Pattern Interface for creating User entities.
 * 
 * This interface defines the contract for creating User objects in a 
 * centralized, consistent manner. It encapsulates the complex logic of
 * user creation including:
 * - Input validation
 * - Default value assignment
 * - Password hashing
 * - Audit field initialization
 * - Account type-specific setup
 * 
 * Design Pattern: Factory Pattern
 * 
 * Intent:
 * - Provide an interface for creating objects without specifying exact class
 * - Encapsulate object creation logic in a single place
 * - Ensure all User objects are created consistently
 * - Make creation logic easy to test and maintain
 * 
 * Benefits:
 * 1. Single Responsibility: Creation logic in one place
 * 2. Validation: All inputs validated before object creation
 * 3. Consistency: All users created with same defaults
 * 4. Testability: Easy to mock for unit tests
 * 5. Maintainability: Change creation logic in one place
 * 
 * Pattern Structure:
 * - UserFactory (this interface) - Defines creation contract
 * - UserFactoryImpl - Concrete implementation
 * - UserService - Client that uses factory
 * 
 * Real-World Analogy:
 * Think of a car factory:
 * - Factory Interface: "I can build cars"
 * - Factory Implementation: Actual assembly line process
 * - Customer (Service): Orders a car, gets finished product
 * - Customer doesn't need to know HOW car is built
 * 
 * When to Use Factory Pattern:
 * - Object creation is complex
 * - Need to enforce constraints/validation
 * - Want to hide creation logic from clients
 * - Multiple ways to create same type of object
 * 
 * Example Usage:
 * <pre>
 * {@code
 * @Service
 * public class UserService {
 *     @Autowired
 *     private UserFactory userFactory;
 *     
 *     public User registerUser(CreateUserRequest request) {
 *         // Factory handles validation, defaults, password hashing
 *         User user = userFactory.createUser(request);
 *         
 *         // Service focuses on business logic
 *         return userRepository.save(user);
 *     }
 * }
 * }
 * </pre>
 * 
 * @see UserFactoryImpl
 * @see User
 */
public interface UserFactory {

    /**
     * Creates a new User entity from a creation request.
     * 
     * This method encapsulates the entire user creation process:
     * 1. Validates email format
     * 2. Validates password strength
     * 3. Validates required fields (name, etc.)
     * 4. Hashes password using BCrypt
     * 5. Sets default values (accountType = BASIC, isActive = true, etc.)
     * 6. Initializes audit fields (createdAt, updatedAt)
     * 7. Applies account type-specific setup
     * 
     * The returned User entity is ready to be persisted to database.
     * 
     * Validation Rules:
     * - Email: Must be valid format (user@domain.com)
     * - Email: Must not already exist (checked by caller)
     * - Password: Minimum 8 characters
     * - Password: Must contain uppercase, lowercase, digit
     * - Name: Required, 2-255 characters
     * 
     * Default Values Set:
     * - accountType: BASIC
     * - isActive: true
     * - emailVerified: false
     * 
     * Example:
     * <pre>
     * {@code
     * CreateUserRequest request = new CreateUserRequest();
     * request.setEmail("john@example.com");
     * request.setPassword("SecurePass123");
     * request.setName("John Doe");
     * 
     * User user = userFactory.createUser(request);
     * // user.getPassword() → BCrypt hash
     * // user.getAccountType() → BASIC
     * // user.getIsActive() → true
     * }
     * </pre>
     * 
     * @param request CreateUserRequest containing user registration data
     * @return User entity ready to be persisted
     * @throws ValidationException if email format is invalid
     * @throws ValidationException if password doesn't meet requirements
     * @throws ValidationException if required fields are missing
     */
    User createUser(CreateUserRequest request);

    /**
     * Creates a premium User entity from a creation request.
     * 
     * Similar to createUser() but sets accountType to PREMIUM and
     * applies stricter validation rules for premium accounts.
     * 
     * Additional Premium Validation:
     * - Headline: Required (min 10 characters)
     * - Summary: Required (min 50 characters)
     * - Location: Required
     * - Profile photo: Recommended
     * 
     * Premium users get:
     * - Unlimited connections
     * - InMail messaging
     * - Advanced search
     * - Profile badge
     * 
     * Example:
     * <pre>
     * {@code
     * CreateUserRequest request = new CreateUserRequest();
     * request.setEmail("jane@example.com");
     * request.setPassword("SecurePass123");
     * request.setName("Jane Smith");
     * request.setHeadline("Senior Product Manager");
     * request.setSummary("10+ years of experience in product management...");
     * 
     * User premiumUser = userFactory.createPremiumUser(request);
     * // premiumUser.getAccountType() → PREMIUM
     * }
     * </pre>
     * 
     * @param request CreateUserRequest containing premium user data
     * @return Premium User entity ready to be persisted
     * @throws ValidationException if premium requirements not met
     */
    User createPremiumUser(CreateUserRequest request);

    /**
     * Updates an existing User entity with new data from update request.
     * 
     * This method updates only the fields provided in the request.
     * Null fields in the request are ignored (not updated).
     * 
     * Fields that can be updated:
     * - name
     * - headline
     * - summary
     * - location
     * - profilePhotoUrl
     * - phoneNumber
     * - dateOfBirth
     * - industry
     * - currentJobTitle
     * - currentCompany
     * 
     * Fields that CANNOT be updated via this method:
     * - email (requires email verification flow)
     * - password (requires old password verification)
     * - accountType (requires payment flow)
     * - isActive (admin-only operation)
     * - emailVerified (separate verification flow)
     * 
     * Example:
     * <pre>
     * {@code
     * User existingUser = userRepository.findById(123L).orElseThrow();
     * 
     * UpdateUserRequest request = new UpdateUserRequest();
     * request.setHeadline("Senior Software Engineer");
     * request.setSummary("Updated bio...");
     * // Other fields are null (not updated)
     * 
     * User updatedUser = userFactory.updateUser(existingUser, request);
     * // updatedUser.getHeadline() → "Senior Software Engineer" (updated)
     * // updatedUser.getName() → unchanged
     * }
     * </pre>
     * 
     * @param existingUser The current User entity to update
     * @param request UpdateUserRequest containing fields to update
     * @return Updated User entity (same object, modified)
     * @throws ValidationException if update violates validation rules
     */
    User updateUser(User existingUser, UpdateUserRequest request);

    /**
     * Creates a User entity with all default values.
     * 
     * Useful for:
     * - Testing: Create test users quickly
     * - System users: Create admin/system accounts
     * - Migrations: Bulk user creation
     * 
     * Creates user with:
     * - Placeholder email: "user_{random}@system.local"
     * - Random password: Securely generated
     * - Name: "Guest User"
     * - accountType: BASIC
     * - isActive: true
     * - emailVerified: false
     * 
     * Example:
     * <pre>
     * {@code
     * // Testing
     * User testUser = userFactory.createDefaultUser();
     * testUser.setEmail("test@example.com");
     * userRepository.save(testUser);
     * }
     * </pre>
     * 
     * @return User entity with default values
     */
    User createDefaultUser();

    /**
     * Validates email format.
     * 
     * Checks if email:
     * - Contains exactly one @ symbol
     * - Has valid domain
     * - Matches email regex pattern
     * 
     * @param email Email address to validate
     * @return true if valid, false otherwise
     */
    boolean isValidEmail(String email);

    /**
     * Validates password strength.
     * 
     * Password must:
     * - Be at least 8 characters
     * - Contain uppercase letter
     * - Contain lowercase letter
     * - Contain digit
     * - Optionally contain special character
     * 
     * @param password Password to validate
     * @return true if strong enough, false otherwise
     */
    boolean isValidPassword(String password);
}

