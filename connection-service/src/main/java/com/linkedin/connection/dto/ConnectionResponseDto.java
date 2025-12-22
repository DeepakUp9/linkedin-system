package com.linkedin.connection.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.linkedin.connection.model.ConnectionState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for returning complete connection information to clients.
 * Used as the response body for connection-related API endpoints.
 * 
 * Design Considerations:
 * - Includes all fields clients need to display connection info
 * - Includes user details for both requester and addressee
 * - Excludes internal database fields (no entity IDs for users)
 * - JsonInclude.NON_NULL omits null fields from JSON response
 * 
 * User Details:
 * - Instead of just returning user IDs, we include name, email, headline
 * - This is fetched from user-service via Feign client
 * - Reduces the number of API calls clients need to make
 * 
 * Example Usage (JSON Response Body):
 * <pre>
 * {@code
 * {
 *   "id": 123,
 *   "requester": {
 *     "userId": 1,
 *     "name": "John Doe",
 *     "email": "john.doe@example.com",
 *     "headline": "Software Engineer at Google"
 *   },
 *   "addressee": {
 *     "userId": 2,
 *     "name": "Jane Smith",
 *     "email": "jane.smith@example.com",
 *     "headline": "CTO at Startup Inc"
 *   },
 *   "state": "ACCEPTED",
 *   "requestedAt": "2025-12-20T10:30:00",
 *   "respondedAt": "2025-12-20T14:45:00",
 *   "message": "Would love to connect!",
 *   "createdAt": "2025-12-20T10:30:00",
 *   "updatedAt": "2025-12-20T14:45:00"
 * }
 * }
 * </pre>
 * 
 * @see com.linkedin.connection.controller.ConnectionController
 * @see com.linkedin.connection.service.ConnectionService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response DTO containing complete connection information")
public class ConnectionResponseDto {

    /**
     * Unique identifier for the connection.
     */
    @Schema(description = "Connection ID", example = "123")
    private Long id;

    /**
     * Details of the user who sent the connection request.
     */
    @Schema(description = "User who sent the connection request")
    private UserSummary requester;

    /**
     * Details of the user who received the connection request.
     */
    @Schema(description = "User who received the connection request")
    private UserSummary addressee;

    /**
     * Current state of the connection.
     */
    @Schema(description = "Current connection state", example = "ACCEPTED")
    private ConnectionState state;

    /**
     * Timestamp when the connection request was sent.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When the connection request was sent", example = "2025-12-20T10:30:00")
    private LocalDateTime requestedAt;

    /**
     * Timestamp when the addressee responded (null if still pending).
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When the addressee responded", example = "2025-12-20T14:45:00")
    private LocalDateTime respondedAt;

    /**
     * Optional message from the requester.
     */
    @Schema(description = "Optional message with the request", example = "Would love to connect!")
    private String message;

    /**
     * When this connection record was created.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When this connection was created", example = "2025-12-20T10:30:00")
    private LocalDateTime createdAt;

    /**
     * When this connection record was last updated.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When this connection was last updated", example = "2025-12-20T14:45:00")
    private LocalDateTime updatedAt;

    /**
     * Nested DTO for user summary information.
     * Combines user ID with basic profile info from user-service.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Summary information about a user")
    public static class UserSummary {
        
        @Schema(description = "User ID", example = "1")
        private Long userId;
        
        @Schema(description = "User's full name", example = "John Doe")
        private String name;
        
        @Schema(description = "User's email", example = "john.doe@example.com")
        private String email;
        
        @Schema(description = "User's professional headline", example = "Software Engineer at Google")
        private String headline;
        
        @Schema(description = "User's profile picture URL", example = "https://example.com/avatar/john.jpg")
        private String profilePictureUrl;
    }
}

