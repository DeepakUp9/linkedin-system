package com.linkedin.user.patterns.strategy;

import com.linkedin.common.exceptions.ValidationException;
import com.linkedin.user.model.AccountType;
import com.linkedin.user.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Concrete Strategy for validating BASIC user profiles.
 * 
 * This class implements the Strategy Pattern for BASIC (free tier) users.
 * It applies minimal validation rules - only essential fields are required.
 * 
 * Design Pattern: Strategy Pattern (Concrete Strategy)
 * 
 * Validation Rules for BASIC Users:
 * 
 * Required Fields:
 * - Email: Must exist, valid format (user@domain.com)
 * - Password: Must exist (assumed already hashed)
 * - Name: Must exist, 2-255 characters
 * 
 * Optional Fields (can be null):
 * - Headline
 * - Summary  
 * - Location
 * - Profile photo URL
 * - Phone number
 * - Date of birth
 * - Industry
 * - Current job title
 * - Current company
 * 
 * Philosophy for BASIC Users:
 * - Low barrier to entry
 * - Get users signed up quickly
 * - Can complete profile later
 * - Encourage upgrades to premium
 * 
 * @see ProfileValidationStrategy
 * @see PremiumProfileValidationStrategy
 */
@Component
@Slf4j
public class BasicProfileValidationStrategy implements ProfileValidationStrategy {

    /**
     * Email validation regex pattern
     * 
     * Validates format: user@domain.com
     * - Local part: alphanumeric, dots, underscores, hyphens
     * - @ symbol (required)
     * - Domain: alphanumeric with dots
     * - TLD: 2-6 characters
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,6}$"
    );

    /**
     * Minimum name length
     */
    private static final int MIN_NAME_LENGTH = 2;

    /**
     * Maximum name length
     */
    private static final int MAX_NAME_LENGTH = 255;

    /**
     * Validates a BASIC user profile.
     * 
     * Checks only essential fields:
     * 1. Email exists and has valid format
     * 2. Password exists (already hashed by factory)
     * 3. Name exists and has valid length
     * 
     * Process:
     * 1. Validate email (required, format)
     * 2. Validate password (required)
     * 3. Validate name (required, length)
     * 4. Optional fields are ignored (can be null)
     * 
     * @param user User entity to validate
     * @throws ValidationException if any required field is invalid
     */
    @Override
    public void validate(User user) {
        log.debug("Validating BASIC user profile: {}", user.getEmail());
        
        // Step 1: Validate email
        validateEmail(user);
        
        // Step 2: Validate password
        validatePassword(user);
        
        // Step 3: Validate name
        validateName(user);
        
        // Note: All other fields are optional for BASIC users
        // No validation needed for headline, summary, location, etc.
        
        log.debug("BASIC user profile validation passed: {}", user.getEmail());
    }

    /**
     * Returns the account type this strategy validates.
     * 
     * @return AccountType.BASIC
     */
    @Override
    public AccountType getAccountType() {
        return AccountType.BASIC;
    }

    /**
     * Returns a description of BASIC validation rules.
     * 
     * @return Human-readable description
     */
    @Override
    public String getValidationDescription() {
        return "BASIC account validation: Email, password, and name (2-255 characters) required. " +
               "All other fields optional.";
    }

    // ============================================
    // Private Validation Methods
    // ============================================

    /**
     * Validates email field.
     * 
     * Rules:
     * 1. Email must not be null
     * 2. Email must not be empty or whitespace
     * 3. Email must match valid format (regex)
     * 
     * @param user User to validate
     * @throws ValidationException if email is invalid
     */
    private void validateEmail(User user) {
        // Check email exists
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            log.warn("BASIC user validation failed: Email is required");
            throw new ValidationException(
                "Email is required for all users",
                "USER_EMAIL_REQUIRED"
            );
        }
        
        // Check email format
        if (!isValidEmailFormat(user.getEmail())) {
            log.warn("BASIC user validation failed: Invalid email format: {}", user.getEmail());
            throw new ValidationException(
                "Email format is invalid: " + user.getEmail(),
                "USER_EMAIL_INVALID_FORMAT"
            );
        }
        
        log.debug("Email validation passed: {}", user.getEmail());
    }

    /**
     * Validates password field.
     * 
     * Rules:
     * 1. Password must not be null
     * 2. Password must not be empty
     * 
     * Note: Password should already be hashed by UserFactory.
     * We only check it exists, not the strength (that's done before hashing).
     * 
     * @param user User to validate
     * @throws ValidationException if password is missing
     */
    private void validatePassword(User user) {
        // Check password exists
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            log.warn("BASIC user validation failed: Password is required");
            throw new ValidationException(
                "Password is required for all users",
                "USER_PASSWORD_REQUIRED"
            );
        }
        
        log.debug("Password validation passed (exists)");
    }

    /**
     * Validates name field.
     * 
     * Rules:
     * 1. Name must not be null
     * 2. Name must not be empty or whitespace
     * 3. Name must be at least 2 characters (after trimming)
     * 4. Name must not exceed 255 characters
     * 
     * @param user User to validate
     * @throws ValidationException if name is invalid
     */
    private void validateName(User user) {
        // Check name exists
        if (user.getName() == null || user.getName().trim().isEmpty()) {
            log.warn("BASIC user validation failed: Name is required");
            throw new ValidationException(
                "Name is required for all users",
                "USER_NAME_REQUIRED"
            );
        }
        
        String trimmedName = user.getName().trim();
        
        // Check minimum length
        if (trimmedName.length() < MIN_NAME_LENGTH) {
            log.warn("BASIC user validation failed: Name too short: {}", trimmedName.length());
            throw new ValidationException(
                String.format("Name must be at least %d characters", MIN_NAME_LENGTH),
                "USER_NAME_TOO_SHORT"
            );
        }
        
        // Check maximum length
        if (user.getName().length() > MAX_NAME_LENGTH) {
            log.warn("BASIC user validation failed: Name too long: {}", user.getName().length());
            throw new ValidationException(
                String.format("Name must not exceed %d characters", MAX_NAME_LENGTH),
                "USER_NAME_TOO_LONG"
            );
        }
        
        log.debug("Name validation passed: {}", trimmedName);
    }

    /**
     * Checks if email has valid format using regex.
     * 
     * Valid examples:
     * - john@example.com
     * - jane.doe@company.co.uk
     * - user+tag@domain.org
     * 
     * Invalid examples:
     * - invalid.email (no @)
     * - @domain.com (no local part)
     * - user@domain (no TLD)
     * - user @domain.com (whitespace)
     * 
     * @param email Email to check
     * @return true if valid format, false otherwise
     */
    private boolean isValidEmailFormat(String email) {
        if (email == null) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}

