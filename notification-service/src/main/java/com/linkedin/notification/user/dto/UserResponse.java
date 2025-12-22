package com.linkedin.notification.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user information received from user-service.
 * 
 * Purpose:
 * - Receive user details from user-service via Feign client
 * - Used to get user email, name for notifications
 * 
 * Fields:
 * - Only includes fields needed for notifications
 * - Full user profile has more fields
 * 
 * @see com.linkedin.notification.client.UserServiceClient
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore unknown fields for backward compatibility
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private String headline;
    private String profilePictureUrl;
    private boolean active;
}

