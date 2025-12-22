package com.linkedin.user.model;

/**
 * Enum representing the type of user account.
 * 
 * This is a mirror of the AccountType enum from user-service.
 * It's duplicated here to avoid direct dependency on user-service module.
 * 
 * Types:
 * - BASIC: Free account with limited features
 * - PREMIUM: Paid account with advanced features
 * 
 * Usage:
 * Used in DTOs when receiving user data from user-service via Feign.
 * 
 * @see com.linkedin.user.dto.UserResponse
 */
public enum AccountType {
    /**
     * Basic/Free account.
     * Limitations:
     * - Limited connection requests per month
     * - Cannot see who viewed profile
     * - Limited search filters
     */
    BASIC,

    /**
     * Premium/Paid account.
     * Benefits:
     * - Unlimited connection requests
     * - See who viewed profile
     * - Advanced search filters
     * - InMail messages
     * - Premium badge on profile
     */
    PREMIUM
}

