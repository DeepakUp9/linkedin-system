package com.linkedin.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for notification statistics.
 * 
 * Purpose:
 * - Provides aggregated notification counts
 * - Used for UI badges, dashboard widgets
 * 
 * Usage:
 * <pre>
 * GET /api/notifications/stats
 * Response:
 * {
 *   "totalNotifications": 150,
 *   "unreadCount": 5,
 *   "readCount": 145,
 *   "failedCount": 0
 * }
 * 
 * Frontend Usage:
 * - Display unread badge: 🔔 5
 * - Show total in dashboard
 * </pre>
 * 
 * Performance:
 * - Cached in Redis (TTL: 5 minutes)
 * - Invalidated when notification read/created
 * 
 * @see com.linkedin.notification.service.NotificationService#getNotificationStats
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Statistics about user's notifications")
public class NotificationStatsDto {

    @Schema(description = "Total number of notifications", example = "150")
    @Builder.Default
    private long totalNotifications = 0L;

    @Schema(description = "Number of unread in-app notifications", example = "5")
    @Builder.Default
    private long unreadCount = 0L;

    @Schema(description = "Number of read notifications", example = "145")
    @Builder.Default
    private long readCount = 0L;

    @Schema(description = "Number of failed notifications (internal)", example = "0")
    @Builder.Default
    private long failedCount = 0L;

    /**
     * Calculate read count from total and unread.
     * 
     * Use Case:
     * When fetching stats, we might only get total and unread from DB
     * 
     * @return Calculated read count
     */
    public long calculateReadCount() {
        return totalNotifications - unreadCount;
    }

    /**
     * Check if user has any unread notifications.
     * 
     * Use Case:
     * Show/hide notification badge in UI
     * 
     * @return true if unread count > 0
     */
    public boolean hasUnread() {
        return unreadCount > 0;
    }

    /**
     * Get percentage of read notifications.
     * 
     * Use Case:
     * Analytics: "User has read 96.7% of their notifications"
     * 
     * @return Percentage (0.0 to 100.0)
     */
    public double getReadPercentage() {
        if (totalNotifications == 0) {
            return 0.0;
        }
        return (readCount / (double) totalNotifications) * 100.0;
    }
}

