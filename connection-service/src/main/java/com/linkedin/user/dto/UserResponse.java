package com.linkedin.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.linkedin.user.model.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for receiving user data from user-service via Feign client.
 * 
 * Purpose:
 * Represents the response structure when calling user-service endpoints.
 * This is used to deserialize JSON responses from user-service.
 * 
 * Example Response from User Service:
 * <pre>
 * GET /api/users/123
 * 
 * {
 *   "id": 123,
 *   "name": "John Doe",
 *   "email": "john.doe@example.com",
 *   "headline": "Software Engineer at Google",
 *   "profilePictureUrl": "https://cdn.example.com/profiles/john.jpg",
 *   "location": "San Francisco, CA",
 *   "industry": "Technology",
 *   "accountType": "PREMIUM",
 *   "active": true,
 *   "createdAt": "2025-01-15T10:30:00"
 * }
 * </pre>
 * 
 * Usage in Connection Service:
 * <pre>
 * {@code
 * // Validate user exists before sending connection request
 * UserResponse addressee = userServiceClient.getUserById(addresseeId);
 * if (!addressee.isActive()) {
 *     throw new ValidationException("Cannot connect to inactive user");
 * }
 * 
 * // Fetch user details for connection DTO
 * UserResponse user = userServiceClient.getUserById(userId);
 * dto.setRequester(ConnectionResponseDto.UserSummary.builder()
 *     .userId(user.getId())
 *     .name(user.getName())
 *     .headline(user.getHeadline())
 *     .build());
 * }
 * </pre>
 * 
 * JSON Mapping:
 * - Uses Jackson for deserialization
 * - @JsonIgnoreProperties ignores unknown fields (backward compatibility)
 * - If user-service adds new fields, this DTO won't break
 * 
 * Design Considerations:
 * - Mirrors user-service's UserResponse structure
 * - Contains only fields needed by connection-service
 * - Avoids tight coupling with user-service internals
 * 
 * Related:
 * @see com.linkedin.user.client.UserServiceClient
 * @see com.linkedin.connection.mapper.ConnectionMapper
 * @see com.linkedin.connection.dto.ConnectionResponseDto.UserSummary
 * 
 * @author LinkedIn System
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore unknown fields from user-service
public class UserResponse {

    /**
     * Unique identifier of the user.
     */
    private Long id;

    /**
     * Full name of the user.
     * Example: "John Doe"
     */
    private String name;

    /**
     * Email address of the user.
     * Example: "john.doe@example.com"
     */
    private String email;

    /**
     * Professional headline/tagline.
     * Example: "Software Engineer at Google | Cloud Architecture Enthusiast"
     */
    private String headline;

    /**
     * URL to the user's profile picture.
     * Example: "https://cdn.example.com/profiles/john.jpg"
     */
    private String profilePictureUrl;

    /**
     * Geographic location of the user.
     * Example: "San Francisco, CA", "New York, NY"
     */
    private String location;

    /**
     * Industry or profession.
     * Example: "Technology", "Finance", "Healthcare"
     */
    private String industry;

    /**
     * Type of account (BASIC or PREMIUM).
     */
    private AccountType accountType;

    /**
     * Whether the user account is active.
     * Inactive accounts:
     * - Deleted by user
     * - Suspended by admin
     * - Deactivated temporarily
     */
    private boolean active;

    /**
     * When the user account was created.
     */
    private LocalDateTime createdAt;

    /**
     * When the user account was last updated.
     */
    private LocalDateTime updatedAt;

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Checks if the user has a premium account.
     * 
     * @return true if PREMIUM, false otherwise
     */
    public boolean isPremium() {
        return AccountType.PREMIUM.equals(accountType);
    }

    /**
     * Checks if the user has a basic account.
     * 
     * @return true if BASIC, false otherwise
     */
    public boolean isBasic() {
        return AccountType.BASIC.equals(accountType);
    }

    /**
     * Checks if the user account is active and can receive connection requests.
     * 
     * @return true if active, false if inactive/deleted/suspended
     */
    public boolean canReceiveConnectionRequests() {
        return active;
    }

    /**
     * Gets a display name for the user.
     * If name is null/empty, falls back to email.
     * 
     * @return Display name
     */
    public String getDisplayName() {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        if (email != null && !email.trim().isEmpty()) {
            return email.split("@")[0]; // Use part before @ as fallback
        }
        return "User " + id;
    }

    /**
     * Gets a summary string for logging.
     * 
     * @return Summary string
     */
    public String toSummaryString() {
        return String.format("User[id=%d, name=%s, email=%s, active=%s]", 
            id, name, email, active);
    }
}

