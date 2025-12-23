package com.linkedin.post.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user data from User Service.
 * Mirrors the UserResponse from user-service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private String displayName;
    private String headline;
    private String profilePictureUrl;
}

