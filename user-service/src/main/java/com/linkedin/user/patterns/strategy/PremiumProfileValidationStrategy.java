package com.linkedin.user.patterns.strategy;

import com.linkedin.common.exceptions.ValidationException;
import com.linkedin.user.model.AccountType;
import com.linkedin.user.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Concrete Strategy for validating PREMIUM user profiles.
 * 
 * This class implements the Strategy Pattern for PREMIUM (paid tier) users.
 * It applies strict validation rules - all BASIC requirements PLUS additional
 * mandatory fields to ensure complete, professional profiles.
 * 
 * Design Pattern: Strategy Pattern (Concrete Strategy)
 * 
 * Validation Rules for PREMIUM Users:
 * 
 * ALL BASIC Requirements:
 * - Email: Must exist, valid format (user@domain.com)
 * - Password: Must exist (assumed already hashed)
 * - Name: Must exist, 2-255 characters
 * 
 * PLUS Additional PREMIUM Requirements:
 * - Headline: REQUIRED, minimum 10 characters
 * - Summary: REQUIRED, minimum 50 characters
 * - Location: REQUIRED, not empty
 * 
 * Optional Fields (recommended but not required):
 * - Profile photo URL
 * - Phone number
 * - Date of birth
 * - Industry
 * - Current job title
 * - Current company
 * 
 * Philosophy for PREMIUM Users:
 * - Higher barrier ensures quality
 * - Premium users expect professional networking
 * - Complete profiles → better matches → better ROI on subscription
 * - Platform reputation: "Premium profiles are always complete"
 * 
 * Business Justification:
 * 1. Premium users pay for service → justify with quality requirements
 * 2. Incomplete premium profiles hurt platform value
 * 3. Complete profiles increase engagement (70% more connections)
 * 4. Professional image attracts more premium users
 * 5. Clear differentiation from BASIC tier
 * 
 * @see ProfileValidationStrategy
 * @see BasicProfileValidationStrategy
 */
@Component
@Slf4j
public class PremiumProfileValidationStrategy implements ProfileValidationStrategy {

    /**
     * Email validation regex pattern (same as BASIC)
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
     * Minimum name length (same as BASIC)
     */
    private static final int MIN_NAME_LENGTH = 2;

    /**
     * Maximum name length (same as BASIC)
     */
    private static final int MAX_NAME_LENGTH = 255;

    /**
     * Minimum headline length (PREMIUM requirement)
     * 
     * Why 10 characters?
     * - Too short: "Developer" (9 chars) - not descriptive
     * - Good: "Senior Java Developer at Tech Corp" (37 chars)
     * - Ensures meaningful professional headline
     */
    private static final int MIN_HEADLINE_LENGTH = 10;

    /**
     * Maximum headline length (database constraint)
     */
    private static final int MAX_HEADLINE_LENGTH = 255;

    /**
     * Minimum summary length (PREMIUM requirement)
     * 
     * Why 50 characters?
     * - Forces users to write meaningful professional summary
     * - Example: "Experienced software engineer with 5+ years in Java" (55 chars)
     * - Prevents lazy summaries: "Good developer" (14 chars)
     * - LinkedIn research: Profiles with 50+ char summaries get 30% more views
     */
    private static final int MIN_SUMMARY_LENGTH = 50;

    /**
     * Maximum summary length (reasonable limit for readability)
     */
    private static final int MAX_SUMMARY_LENGTH = 2000;

    /**
     * Minimum location length (must be meaningful)
     */
    private static final int MIN_LOCATION_LENGTH = 3;

    /**
     * Maximum location length (database constraint)
     */
    private static final int MAX_LOCATION_LENGTH = 255;

    /**
     * Validates a PREMIUM user profile.
     * 
     * Checks ALL BASIC requirements PLUS additional PREMIUM requirements:
     * 
     * BASIC Validation (inherited requirements):
     * 1. Email exists and has valid format
     * 2. Password exists (already hashed by factory)
     * 3. Name exists and has valid length (2-255)
     * 
     * PREMIUM Validation (additional requirements):
     * 4. Headline exists and has valid length (10-255)
     * 5. Summary exists and has valid length (50-2000)
     * 6. Location exists and has valid length (3-255)
     * 
     * Process:
     * 1. Validate ALL basic fields (email, password, name)
     * 2. Validate PREMIUM-specific fields (headline, summary, location)
     * 3. Throw ValidationException if ANY field is invalid
     * 4. Return silently if all validations pass
     * 
     * Why Duplicate Basic Validation?
     * - Each strategy is self-contained and independent
     * - No inheritance/delegation between strategies (clean separation)
     * - Premium strategy can work standalone without BasicStrategy
     * - Easier to test and maintain
     * 
     * @param user User entity to validate
     * @throws ValidationException if any required field is invalid
     */
    @Override
    public void validate(User user) {
        log.debug("Validating PREMIUM user profile: {}", user.getEmail());
        
        // ============================================
        // STEP 1: Validate BASIC Requirements
        // ============================================
        validateEmail(user);
        validatePassword(user);
        validateName(user);
        
        // ============================================
        // STEP 2: Validate PREMIUM Requirements
        // ============================================
        validateHeadline(user);
        validateSummary(user);
        validateLocation(user);
        
        log.debug("PREMIUM user profile validation passed: {}", user.getEmail());
    }

    /**
     * Returns the account type this strategy validates.
     * 
     * @return AccountType.PREMIUM
     */
    @Override
    public AccountType getAccountType() {
        return AccountType.PREMIUM;
    }

    /**
     * Returns a description of PREMIUM validation rules.
     * 
     * @return Human-readable description
     */
    @Override
    public String getValidationDescription() {
        return "PREMIUM account validation: All BASIC requirements (email, password, name) " +
               "PLUS headline (10+ chars), summary (50+ chars), and location required.";
    }

    // ============================================
    // BASIC Validation Methods (inherited rules)
    // ============================================

    /**
     * Validates email field (same as BASIC).
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
            log.warn("PREMIUM user validation failed: Email is required");
            throw new ValidationException(
                "Email is required for all users",
                "USER_EMAIL_REQUIRED"
            );
        }
        
        // Check email format
        if (!isValidEmailFormat(user.getEmail())) {
            log.warn("PREMIUM user validation failed: Invalid email format: {}", user.getEmail());
            throw new ValidationException(
                "Email format is invalid: " + user.getEmail(),
                "USER_EMAIL_INVALID_FORMAT"
            );
        }
        
        log.debug("Email validation passed: {}", user.getEmail());
    }

    /**
     * Validates password field (same as BASIC).
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
            log.warn("PREMIUM user validation failed: Password is required");
            throw new ValidationException(
                "Password is required for all users",
                "USER_PASSWORD_REQUIRED"
            );
        }
        
        log.debug("Password validation passed (exists)");
    }

    /**
     * Validates name field (same as BASIC).
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
            log.warn("PREMIUM user validation failed: Name is required");
            throw new ValidationException(
                "Name is required for all users",
                "USER_NAME_REQUIRED"
            );
        }
        
        String trimmedName = user.getName().trim();
        
        // Check minimum length
        if (trimmedName.length() < MIN_NAME_LENGTH) {
            log.warn("PREMIUM user validation failed: Name too short: {}", trimmedName.length());
            throw new ValidationException(
                String.format("Name must be at least %d characters", MIN_NAME_LENGTH),
                "USER_NAME_TOO_SHORT"
            );
        }
        
        // Check maximum length
        if (user.getName().length() > MAX_NAME_LENGTH) {
            log.warn("PREMIUM user validation failed: Name too long: {}", user.getName().length());
            throw new ValidationException(
                String.format("Name must not exceed %d characters", MAX_NAME_LENGTH),
                "USER_NAME_TOO_LONG"
            );
        }
        
        log.debug("Name validation passed: {}", trimmedName);
    }

    // ============================================
    // PREMIUM Validation Methods (additional rules)
    // ============================================

    /**
     * Validates headline field (PREMIUM requirement).
     * 
     * Rules:
     * 1. Headline must not be null
     * 2. Headline must not be empty or whitespace
     * 3. Headline must be at least 10 characters (after trimming)
     * 4. Headline must not exceed 255 characters
     * 
     * Business Logic:
     * - Headline is the first thing other users see
     * - Premium profiles must be professional and complete
     * - Short headlines ("Developer") are not descriptive enough
     * - Good headline: "Senior Java Developer | Cloud Architect | AWS Certified"
     * 
     * @param user User to validate
     * @throws ValidationException if headline is invalid
     */
    private void validateHeadline(User user) {
        // Check headline exists
        if (user.getHeadline() == null || user.getHeadline().trim().isEmpty()) {
            log.warn("PREMIUM user validation failed: Headline is required");
            throw new ValidationException(
                "Headline is required for PREMIUM users",
                "PREMIUM_HEADLINE_REQUIRED"
            );
        }
        
        String trimmedHeadline = user.getHeadline().trim();
        
        // Check minimum length
        if (trimmedHeadline.length() < MIN_HEADLINE_LENGTH) {
            log.warn("PREMIUM user validation failed: Headline too short: {} chars", 
                trimmedHeadline.length());
            throw new ValidationException(
                String.format("Headline must be at least %d characters for PREMIUM users", 
                    MIN_HEADLINE_LENGTH),
                "PREMIUM_HEADLINE_TOO_SHORT"
            );
        }
        
        // Check maximum length
        if (user.getHeadline().length() > MAX_HEADLINE_LENGTH) {
            log.warn("PREMIUM user validation failed: Headline too long: {} chars", 
                user.getHeadline().length());
            throw new ValidationException(
                String.format("Headline must not exceed %d characters", MAX_HEADLINE_LENGTH),
                "PREMIUM_HEADLINE_TOO_LONG"
            );
        }
        
        log.debug("Headline validation passed: {}", trimmedHeadline);
    }

    /**
     * Validates summary field (PREMIUM requirement).
     * 
     * Rules:
     * 1. Summary must not be null
     * 2. Summary must not be empty or whitespace
     * 3. Summary must be at least 50 characters (after trimming)
     * 4. Summary must not exceed 2000 characters
     * 
     * Business Logic:
     * - Summary is the "About" section - critical for premium profiles
     * - 50 characters forces meaningful content
     * - Research shows profiles with summaries get 30% more profile views
     * - Premium users should showcase their experience and skills
     * 
     * Good Summary Example:
     * "Experienced software engineer with 8+ years in Java, Spring Boot, and microservices.
     *  Passionate about building scalable systems and mentoring junior developers.
     *  AWS Certified Solutions Architect." (175 chars)
     * 
     * Bad Summary Example:
     * "Good developer" (14 chars) - rejected!
     * 
     * @param user User to validate
     * @throws ValidationException if summary is invalid
     */
    private void validateSummary(User user) {
        // Check summary exists
        if (user.getSummary() == null || user.getSummary().trim().isEmpty()) {
            log.warn("PREMIUM user validation failed: Summary is required");
            throw new ValidationException(
                "Summary is required for PREMIUM users",
                "PREMIUM_SUMMARY_REQUIRED"
            );
        }
        
        String trimmedSummary = user.getSummary().trim();
        
        // Check minimum length
        if (trimmedSummary.length() < MIN_SUMMARY_LENGTH) {
            log.warn("PREMIUM user validation failed: Summary too short: {} chars", 
                trimmedSummary.length());
            throw new ValidationException(
                String.format("Summary must be at least %d characters for PREMIUM users", 
                    MIN_SUMMARY_LENGTH),
                "PREMIUM_SUMMARY_TOO_SHORT"
            );
        }
        
        // Check maximum length
        if (user.getSummary().length() > MAX_SUMMARY_LENGTH) {
            log.warn("PREMIUM user validation failed: Summary too long: {} chars", 
                user.getSummary().length());
            throw new ValidationException(
                String.format("Summary must not exceed %d characters", MAX_SUMMARY_LENGTH),
                "PREMIUM_SUMMARY_TOO_LONG"
            );
        }
        
        log.debug("Summary validation passed: {} chars", trimmedSummary.length());
    }

    /**
     * Validates location field (PREMIUM requirement).
     * 
     * Rules:
     * 1. Location must not be null
     * 2. Location must not be empty or whitespace
     * 3. Location must be at least 3 characters (after trimming)
     * 4. Location must not exceed 255 characters
     * 
     * Business Logic:
     * - Location is critical for networking (local events, job opportunities)
     * - Premium users should be discoverable by location
     * - Helps with targeted recommendations and connections
     * 
     * Valid Examples:
     * - "San Francisco, CA"
     * - "London, UK"
     * - "Remote"
     * - "New York City, USA"
     * 
     * Invalid Examples:
     * - "SF" (too short, 2 chars)
     * - "" (empty)
     * 
     * @param user User to validate
     * @throws ValidationException if location is invalid
     */
    private void validateLocation(User user) {
        // Check location exists
        if (user.getLocation() == null || user.getLocation().trim().isEmpty()) {
            log.warn("PREMIUM user validation failed: Location is required");
            throw new ValidationException(
                "Location is required for PREMIUM users",
                "PREMIUM_LOCATION_REQUIRED"
            );
        }
        
        String trimmedLocation = user.getLocation().trim();
        
        // Check minimum length
        if (trimmedLocation.length() < MIN_LOCATION_LENGTH) {
            log.warn("PREMIUM user validation failed: Location too short: {} chars", 
                trimmedLocation.length());
            throw new ValidationException(
                String.format("Location must be at least %d characters for PREMIUM users", 
                    MIN_LOCATION_LENGTH),
                "PREMIUM_LOCATION_TOO_SHORT"
            );
        }
        
        // Check maximum length
        if (user.getLocation().length() > MAX_LOCATION_LENGTH) {
            log.warn("PREMIUM user validation failed: Location too long: {} chars", 
                user.getLocation().length());
            throw new ValidationException(
                String.format("Location must not exceed %d characters", MAX_LOCATION_LENGTH),
                "PREMIUM_LOCATION_TOO_LONG"
            );
        }
        
        log.debug("Location validation passed: {}", trimmedLocation);
    }

    // ============================================
    // Helper Methods
    // ============================================

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

