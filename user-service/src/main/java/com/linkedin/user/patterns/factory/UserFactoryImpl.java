package com.linkedin.user.patterns.factory;

import com.linkedin.common.exceptions.ValidationException;
import com.linkedin.user.dto.CreateUserRequest;
import com.linkedin.user.dto.UpdateUserRequest;
import com.linkedin.user.model.AccountType;
import com.linkedin.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Concrete implementation of UserFactory interface.
 * 
 * This class implements the Factory Pattern for creating User entities.
 * It encapsulates all the complex logic needed to create valid User objects:
 * - Input validation (email, password, required fields)
 * - Password hashing using BCrypt
 * - Default value assignment
 * - Account type-specific setup
 * 
 * Design Pattern: Factory Pattern
 * 
 * Components:
 * - UserFactory (interface) - Defines contract
 * - UserFactoryImpl (this class) - Implements contract
 * - PasswordEncoder - Injected dependency for password hashing
 * 
 * Benefits of this implementation:
 * 1. Single place for all user creation logic
 * 2. All users created consistently
 * 3. Validation centralized
 * 4. Easy to test in isolation
 * 5. Easy to change creation logic
 * 
 * @see UserFactory
 * @see User
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserFactoryImpl implements UserFactory {

    /**
     * Email validation regex pattern
     * 
     * Validates format: user@domain.com
     * - Local part: alphanumeric, dots, underscores, hyphens
     * - @ symbol
     * - Domain: alphanumeric with dots
     * - TLD: 2-6 characters
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,6}$"
    );

    /**
     * Password validation regex pattern
     * 
     * Requires:
     * - At least 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$"
    );

    /**
     * PasswordEncoder for hashing passwords
     * 
     * Injected by Spring (usually BCryptPasswordEncoder)
     * - Hashes passwords before storage
     * - Never stores plain text passwords
     * - Salt automatically added for security
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates a new User entity with BASIC account type.
     * 
     * Process:
     * 1. Validate all inputs
     * 2. Hash password with BCrypt
     * 3. Create User entity
     * 4. Set default values
     * 5. Return ready-to-persist entity
     * 
     * @param request CreateUserRequest with user data
     * @return User entity ready to save
     * @throws ValidationException if validation fails
     */
    @Override
    public User createUser(CreateUserRequest request) {
        log.debug("Creating new user with email: {}", request.getEmail());
        
        // Step 1: Validate inputs
        validateCreateUserRequest(request);
        
        // Step 2: Hash password
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        
        // Step 3: Build User entity using Lombok builder
        User user = User.builder()
            // Required fields
            .email(request.getEmail())
            .password(hashedPassword)  // NEVER store plain text!
            .name(request.getName())
            
            // Optional profile fields
            .headline(request.getHeadline())
            .summary(request.getSummary())
            .location(request.getLocation())
            .profilePhotoUrl(request.getProfilePhotoUrl())
            
            // Optional contact/personal fields
            .phoneNumber(request.getPhoneNumber())
            .dateOfBirth(request.getDateOfBirth())
            
            // Optional professional fields
            .industry(request.getIndustry())
            .currentJobTitle(request.getCurrentJobTitle())
            .currentCompany(request.getCurrentCompany())
            
            // Default values
            .accountType(AccountType.BASIC)
            .isActive(true)
            .emailVerified(false)
            
            .build();
        
        log.debug("User created successfully: {}", user.getEmail());
        
        return user;
    }

    /**
     * Creates a new User entity with PREMIUM account type.
     * 
     * Applies stricter validation rules for premium users:
     * - Headline required (min 10 characters)
     * - Summary required (min 50 characters)
     * - Location required
     * 
     * @param request CreateUserRequest with premium user data
     * @return Premium User entity ready to save
     * @throws ValidationException if premium requirements not met
     */
    @Override
    public User createPremiumUser(CreateUserRequest request) {
        log.debug("Creating new premium user with email: {}", request.getEmail());
        
        // Step 1: Validate all inputs (including premium requirements)
        validateCreateUserRequest(request);
        validatePremiumRequirements(request);
        
        // Step 2: Hash password
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        
        // Step 3: Build Premium User entity
        User user = User.builder()
            .email(request.getEmail())
            .password(hashedPassword)
            .name(request.getName())
            .headline(request.getHeadline())
            .summary(request.getSummary())
            .location(request.getLocation())
            .profilePhotoUrl(request.getProfilePhotoUrl())
            .phoneNumber(request.getPhoneNumber())
            .dateOfBirth(request.getDateOfBirth())
            .industry(request.getIndustry())
            .currentJobTitle(request.getCurrentJobTitle())
            .currentCompany(request.getCurrentCompany())
            
            // Premium defaults
            .accountType(AccountType.PREMIUM)  // Different from basic
            .isActive(true)
            .emailVerified(false)  // Still needs verification
            
            .build();
        
        log.debug("Premium user created successfully: {}", user.getEmail());
        
        return user;
    }

    /**
     * Updates existing User entity with new data.
     * 
     * Only updates non-null fields from request.
     * Null fields are ignored (not updated).
     * 
     * @param existingUser The user to update
     * @param request UpdateUserRequest with new data
     * @return Updated User entity (same object, modified)
     */
    @Override
    public User updateUser(User existingUser, UpdateUserRequest request) {
        log.debug("Updating user: {}", existingUser.getEmail());
        
        // Update only non-null fields
        if (request.getName() != null) {
            validateName(request.getName());
            existingUser.setName(request.getName());
        }
        
        if (request.getHeadline() != null) {
            existingUser.setHeadline(request.getHeadline());
        }
        
        if (request.getSummary() != null) {
            existingUser.setSummary(request.getSummary());
        }
        
        if (request.getLocation() != null) {
            existingUser.setLocation(request.getLocation());
        }
        
        if (request.getProfilePhotoUrl() != null) {
            existingUser.setProfilePhotoUrl(request.getProfilePhotoUrl());
        }
        
        if (request.getPhoneNumber() != null) {
            existingUser.setPhoneNumber(request.getPhoneNumber());
        }
        
        if (request.getDateOfBirth() != null) {
            existingUser.setDateOfBirth(request.getDateOfBirth());
        }
        
        if (request.getIndustry() != null) {
            existingUser.setIndustry(request.getIndustry());
        }
        
        if (request.getCurrentJobTitle() != null) {
            existingUser.setCurrentJobTitle(request.getCurrentJobTitle());
        }
        
        if (request.getCurrentCompany() != null) {
            existingUser.setCurrentCompany(request.getCurrentCompany());
        }
        
        log.debug("User updated successfully: {}", existingUser.getEmail());
        
        return existingUser;
    }

    /**
     * Creates a User with default/placeholder values.
     * 
     * Useful for testing and system users.
     * 
     * @return User with default values
     */
    @Override
    public User createDefaultUser() {
        log.debug("Creating default user");
        
        String randomEmail = "user_" + UUID.randomUUID().toString().substring(0, 8) + "@system.local";
        String randomPassword = UUID.randomUUID().toString();
        
        return User.builder()
            .email(randomEmail)
            .password(passwordEncoder.encode(randomPassword))
            .name("Guest User")
            .accountType(AccountType.BASIC)
            .isActive(true)
            .emailVerified(false)
            .build();
    }

    /**
     * Validates email format using regex.
     * 
     * @param email Email to validate
     * @return true if valid, false otherwise
     */
    @Override
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validates password strength using regex.
     * 
     * Requirements:
     * - At least 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     * 
     * @param password Password to validate
     * @return true if strong enough, false otherwise
     */
    @Override
    public boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(password).matches();
    }

    // ============================================
    // Private Helper Methods
    // ============================================

    /**
     * Validates all fields in CreateUserRequest.
     * 
     * @param request Request to validate
     * @throws ValidationException if validation fails
     */
    private void validateCreateUserRequest(CreateUserRequest request) {
        // Validate email
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ValidationException("Email is required", "USER_EMAIL_REQUIRED");
        }
        
        if (!isValidEmail(request.getEmail())) {
            throw new ValidationException(
                "Email format is invalid: " + request.getEmail(),
                "USER_EMAIL_INVALID"
            );
        }
        
        // Validate password
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new ValidationException("Password is required", "USER_PASSWORD_REQUIRED");
        }
        
        if (!isValidPassword(request.getPassword())) {
            throw new ValidationException(
                "Password must be at least 8 characters and contain uppercase, lowercase, and digit",
                "USER_PASSWORD_WEAK"
            );
        }
        
        // Validate name
        validateName(request.getName());
    }

    /**
     * Validates name field.
     * 
     * @param name Name to validate
     * @throws ValidationException if name is invalid
     */
    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Name is required", "USER_NAME_REQUIRED");
        }
        
        if (name.trim().length() < 2) {
            throw new ValidationException(
                "Name must be at least 2 characters",
                "USER_NAME_TOO_SHORT"
            );
        }
        
        if (name.length() > 255) {
            throw new ValidationException(
                "Name must not exceed 255 characters",
                "USER_NAME_TOO_LONG"
            );
        }
    }

    /**
     * Validates premium user requirements.
     * 
     * Premium users must have:
     * - Headline (min 10 characters)
     * - Summary (min 50 characters)
     * - Location
     * 
     * @param request Request to validate
     * @throws ValidationException if premium requirements not met
     */
    private void validatePremiumRequirements(CreateUserRequest request) {
        // Headline required
        if (request.getHeadline() == null || request.getHeadline().trim().isEmpty()) {
            throw new ValidationException(
                "Premium users must provide a headline",
                "PREMIUM_HEADLINE_REQUIRED"
            );
        }
        
        if (request.getHeadline().trim().length() < 10) {
            throw new ValidationException(
                "Premium user headline must be at least 10 characters",
                "PREMIUM_HEADLINE_TOO_SHORT"
            );
        }
        
        // Summary required
        if (request.getSummary() == null || request.getSummary().trim().isEmpty()) {
            throw new ValidationException(
                "Premium users must provide a summary",
                "PREMIUM_SUMMARY_REQUIRED"
            );
        }
        
        if (request.getSummary().trim().length() < 50) {
            throw new ValidationException(
                "Premium user summary must be at least 50 characters",
                "PREMIUM_SUMMARY_TOO_SHORT"
            );
        }
        
        // Location required
        if (request.getLocation() == null || request.getLocation().trim().isEmpty()) {
            throw new ValidationException(
                "Premium users must provide a location",
                "PREMIUM_LOCATION_REQUIRED"
            );
        }
    }
}

