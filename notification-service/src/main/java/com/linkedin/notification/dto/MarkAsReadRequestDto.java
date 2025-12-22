package com.linkedin.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for marking notifications as read.
 * 
 * Purpose:
 * - Request body for marking one or more notifications as read
 * - Supports both single and batch operations
 * 
 * Usage Examples:
 * 
 * 1. Mark single notification as read:
 *    PUT /api/notifications/mark-as-read
 *    {
 *      "notificationIds": [123]
 *    }
 * 
 * 2. Mark multiple notifications as read:
 *    PUT /api/notifications/mark-as-read
 *    {
 *      "notificationIds": [123, 124, 125]
 *    }
 * 
 * 3. Mark ALL as read:
 *    PUT /api/notifications/mark-all-as-read
 *    (No request body needed)
 * 
 * Validation:
 * - notificationIds must not be empty
 * - Each ID must exist and belong to the authenticated user
 * 
 * @see com.linkedin.notification.controller.NotificationController#markAsRead
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to mark notifications as read")
public class MarkAsReadRequestDto {

    @Schema(
        description = "List of notification IDs to mark as read",
        example = "[1, 2, 3]",
        required = true
    )
    @NotEmpty(message = "Notification IDs list cannot be empty")
    private List<Long> notificationIds;

    /**
     * Check if this is a batch operation.
     * 
     * @return true if marking more than one notification
     */
    public boolean isBatchOperation() {
        return notificationIds != null && notificationIds.size() > 1;
    }

    /**
     * Get the count of notifications to mark.
     * 
     * @return Number of notification IDs
     */
    public int getCount() {
        return notificationIds != null ? notificationIds.size() : 0;
    }
}

