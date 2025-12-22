package com.linkedin.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.linkedin.notification.model.NotificationChannel;
import com.linkedin.notification.model.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for notification responses sent to clients.
 * 
 * Purpose:
 * - Represents a notification in API responses
 * - Hides internal fields (errorMessage, retryCount, metadata)
 * - Provides clean, user-friendly data structure
 * 
 * Usage:
 * <pre>
 * GET /api/notifications
 * Response:
 * [
 *   {
 *     "id": 1,
 *     "type": "CONNECTION_ACCEPTED",
 *     "title": "Connection Accepted",
 *     "message": "John Doe accepted your connection request",
 *     "isRead": false,
 *     "createdAt": "2024-01-15T10:30:00",
 *     "actionLink": "/profile/123"
 *   }
 * ]
 * </pre>
 * 
 * Mapping:
 * Notification Entity → NotificationResponseDto (via NotificationMapper)
 * 
 * @see com.linkedin.notification.model.Notification
 * @see com.linkedin.notification.mapper.NotificationMapper
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't include null fields in JSON
@Schema(description = "Response DTO for notification details")
public class NotificationResponseDto {

    @Schema(description = "Unique identifier of the notification", example = "1")
    private Long id;

    @Schema(description = "Type of notification", example = "CONNECTION_ACCEPTED")
    private NotificationType type;

    @Schema(description = "Delivery channel", example = "IN_APP")
    private NotificationChannel channel;

    @Schema(description = "Notification title", example = "Connection Accepted")
    private String title;

    @Schema(description = "Notification message", example = "John Doe accepted your connection request")
    private String message;

    @Schema(description = "URL to navigate when notification is clicked", example = "/profile/123")
    private String actionLink;

    @Schema(description = "Icon or image URL", example = "https://cdn.example.com/avatar.jpg")
    private String iconUrl;

    @Schema(description = "Whether the notification has been read", example = "false")
    private boolean isRead;

    @Schema(description = "When the notification was read", example = "2024-01-15T10:35:00")
    private LocalDateTime readAt;

    @Schema(description = "When the notification was created", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;

    /**
     * Nested DTO for related entity information.
     * 
     * Example:
     * {
     *   "entityType": "CONNECTION",
     *   "entityId": "456"
     * }
     */
    @Schema(description = "Information about the related entity (e.g., connection, post)")
    private RelatedEntityDto relatedEntity;

    /**
     * Inner class for related entity information.
     * 
     * Why Inner Class?
     * - Only used within NotificationResponseDto
     * - Keeps code organized
     * - Clear ownership
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Information about the entity related to this notification")
    public static class RelatedEntityDto {
        
        @Schema(description = "Type of the related entity", example = "CONNECTION")
        private String entityType;
        
        @Schema(description = "ID of the related entity", example = "456")
        private String entityId;
    }

    /**
     * Helper method to check if notification is recent (within last 24 hours).
     * 
     * Use Case:
     * Frontend can show "New" badge for recent notifications
     * 
     * @return true if created within last 24 hours
     */
    public boolean isRecent() {
        if (createdAt == null) {
            return false;
        }
        return createdAt.isAfter(LocalDateTime.now().minusHours(24));
    }

    /**
     * Helper method to get human-readable time ago.
     * 
     * Use Case:
     * Display "5 minutes ago" instead of timestamp
     * 
     * Note: In real app, this would be done on frontend for i18n
     * 
     * @return String like "5 minutes ago"
     */
    public String getTimeAgo() {
        if (createdAt == null) {
            return "Unknown";
        }
        
        LocalDateTime now = LocalDateTime.now();
        long minutes = java.time.Duration.between(createdAt, now).toMinutes();
        
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " minutes ago";
        
        long hours = minutes / 60;
        if (hours < 24) return hours + " hours ago";
        
        long days = hours / 24;
        if (days < 7) return days + " days ago";
        
        long weeks = days / 7;
        return weeks + " weeks ago";
    }
}

