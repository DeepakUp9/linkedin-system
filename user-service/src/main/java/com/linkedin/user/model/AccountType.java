package com.linkedin.user.model;

/**
 * Enum representing the type of user account.
 * 
 * This enum is used to:
 * 1. Differentiate between free and paid users
 * 2. Apply appropriate validation strategies (Strategy Pattern)
 * 3. Control access to premium features
 * 4. Display account badges in UI
 * 
 * Design Pattern Connection:
 * - Used by ProfileValidationStrategy to select validation rules
 * - BASIC users: Minimal validation requirements
 * - PREMIUM users: Stricter validation requirements (complete profile)
 * 
 * Database Storage:
 * - Stored as VARCHAR in users table
 * - JPA @Enumerated(EnumType.STRING) converts enum to string
 */
public enum AccountType {
    
    /**
     * Basic (Free) Account
     * 
     * Features:
     * - Create profile
     * - Connect with others (limited)
     * - View jobs
     * - Basic search
     * 
     * Validation:
     * - Email required
     * - Name required
     * - Password required
     */
    BASIC("Basic", "Free account with essential features"),
    
    /**
     * Premium (Paid) Account
     * 
     * Features:
     * - All BASIC features
     * - Unlimited connections
     * - InMail messaging
     * - Advanced search filters
     * - Profile badge
     * - Who viewed your profile
     * 
     * Validation:
     * - All BASIC requirements
     * - Headline required (min 10 chars)
     * - Summary required (min 50 chars)
     * - Profile photo required
     * - Location required
     */
    PREMIUM("Premium", "Paid account with advanced features");
    
    /**
     * Human-readable display name
     */
    private final String displayName;
    
    /**
     * Description of account type
     */
    private final String description;
    
    /**
     * Constructor for enum constants
     * 
     * @param displayName Human-readable name
     * @param description Account type description
     */
    AccountType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * Gets the display name
     * 
     * @return Display name for UI
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Gets the description
     * 
     * @return Account type description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Checks if this is a premium account
     * 
     * @return true if premium, false otherwise
     */
    public boolean isPremium() {
        return this == PREMIUM;
    }
    
    /**
     * Checks if this is a basic account
     * 
     * @return true if basic, false otherwise
     */
    public boolean isBasic() {
        return this == BASIC;
    }
}

