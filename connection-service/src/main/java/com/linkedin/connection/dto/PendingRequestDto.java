package com.linkedin.connection.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for displaying pending connection requests.
 * Optimized for the "Pending Requests" view in the UI.
 * 
 * Design Considerations:
 * - Lighter than ConnectionResponseDto (no full connection details)
 * - Focuses on the other user (not the authenticated user)
 * - Includes action buttons context (Accept/Reject for received, Cancel for sent)
 * - Sorted by most recent first
 * 
 * Use Cases:
 * - "Show me pending requests I received" (inbox view)
 * - "Show me pending requests I sent" (outbox view)
 * 
 * Example Usage (JSON Response Body):
 * <pre>
 * {@code
 * {
 *   "connectionId": 123,
 *   "otherUser": {
 *     "userId": 2,
 *     "name": "Jane Smith",
 *     "headline": "CTO at Startup Inc",
 *     "profilePictureUrl": "https://example.com/avatar/jane.jpg"
 *   },
 *   "message": "Would love to connect!",
 *   "requestedAt": "2025-12-20T10:30:00",
 *   "isSentByMe": false,
 *   "mutualConnections": 5
 * }
 * }
 * </pre>
 * 
 * @see com.linkedin.connection.controller.ConnectionController#getPendingRequests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "DTO for displaying pending connection requests")
public class PendingRequestDto {

    /**
     * Connection ID (for accept/reject/cancel actions).
     */
    @Schema(description = "Connection ID", example = "123")
    private Long connectionId;

    /**
     * Summary of the other user involved in the connection.
     * If viewing received requests: this is the requester
     * If viewing sent requests: this is the addressee
     */
    @Schema(description = "The other user in the connection")
    private OtherUserSummary otherUser;

    /**
     * Optional message with the request.
     */
    @Schema(description = "Message included with the request", example = "Would love to connect!")
    private String message;

    /**
     * When the request was sent.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When the request was sent", example = "2025-12-20T10:30:00")
    private LocalDateTime requestedAt;

    /**
     * Whether this request was sent by the authenticated user.
     * - true: I sent this request (show "Cancel" button)
     * - false: I received this request (show "Accept/Reject" buttons)
     */
    @Schema(description = "Whether this request was sent by me", example = "false")
    private Boolean isSentByMe;

    /**
     * Number of mutual connections with the other user.
     * Helps users decide whether to accept.
     */
    @Schema(description = "Number of mutual connections", example = "5")
    private Integer mutualConnections;

    /**
     * Nested DTO for the other user's summary.
     * Lighter than UserSummary in ConnectionResponseDto (no email).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Summary of the other user")
    public static class OtherUserSummary {
        
        @Schema(description = "User ID", example = "2")
        private Long userId;
        
        @Schema(description = "User's full name", example = "Jane Smith")
        private String name;
        
        @Schema(description = "User's professional headline", example = "CTO at Startup Inc")
        private String headline;
        
        @Schema(description = "User's profile picture URL", example = "https://example.com/avatar/jane.jpg")
        private String profilePictureUrl;
        
        @Schema(description = "User's account type", example = "PREMIUM")
        private String accountType;
    }
}

