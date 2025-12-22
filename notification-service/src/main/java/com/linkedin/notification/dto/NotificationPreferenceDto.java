package com.linkedin.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.linkedin.notification.model.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for notification preference management.
 * 
 * Purpose:
 * - Used in both requests and responses
 * - Represents user's channel preferences for a specific notification type
 * 
 * Usage Examples:
 * 
 * 1. GET /api/notifications/preferences
 *    Response:
 *    [
 *      {
 *        "notificationType": "CONNECTION_ACCEPTED",
 *        "emailEnabled": true,
 *        "inAppEnabled": true,
 *        "pushEnabled": false
 *      },
 *      {
 *        "notificationType": "POST_LIKED",
 *        "emailEnabled": false,
 *        "inAppEnabled": true,
 *        "pushEnabled": false
 *      }
 *    ]
 * 
 * 2. PUT /api/notifications/preferences
 *    Request Body:
 *    {
 *      "notificationType": "CONNECTION_ACCEPTED",
 *      "emailEnabled": false,    ← User disables email
 *      "inAppEnabled": true,
 *      "pushEnabled": false
 *    }
 * 
 * Validation:
 * - notificationType: required
 * - At least one channel should be enabled (checked in service layer)
 * 
 * @see com.linkedin.notification.model.NotificationPreference
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "DTO for notification channel preferences")
public class NotificationPreferenceDto {

    @Schema(description = "Type of notification", example = "CONNECTION_ACCEPTED", required = true)
    @NotNull(message = "Notification type is required")
    private NotificationType notificationType;

    @Schema(description = "Whether email notifications are enabled for this type", example = "true")
    @Builder.Default
    private boolean emailEnabled = true;

    @Schema(description = "Whether in-app notifications are enabled for this type", example = "true")
    @Builder.Default
    private boolean inAppEnabled = true;

    @Schema(description = "Whether push notifications are enabled for this type", example = "false")
    @Builder.Default
    private boolean pushEnabled = false;

    @Schema(description = "Whether SMS notifications are enabled for this type", example = "false")
    @Builder.Default
    private boolean smsEnabled = false;

    /**
     * Helper method to check if any channel is enabled.
     * 
     * Use Case:
     * Validation: Don't allow user to disable ALL channels for a notification type
     * 
     * @return true if at least one channel is enabled
     */
    public boolean hasAnyChannelEnabled() {
        return emailEnabled || inAppEnabled || pushEnabled || smsEnabled;
    }

    /**
     * Get human-readable list of enabled channels.
     * 
     * Use Case:
     * UI display: "Enabled channels: Email, In-App"
     * 
     * @return Comma-separated list of enabled channels
     */
    public String getEnabledChannelsString() {
        StringBuilder sb = new StringBuilder();
        if (emailEnabled) sb.append("Email, ");
        if (inAppEnabled) sb.append("In-App, ");
        if (pushEnabled) sb.append("Push, ");
        if (smsEnabled) sb.append("SMS, ");
        
        if (sb.length() > 0) {
            // Remove trailing comma and space
            sb.setLength(sb.length() - 2);
        } else {
            return "None";
        }
        
        return sb.toString();
    }
}

