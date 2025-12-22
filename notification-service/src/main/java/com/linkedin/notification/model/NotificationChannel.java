package com.linkedin.notification.model;

import lombok.Getter;

/**
 * Enum representing different notification delivery channels.
 * 
 * Channels:
 * - IN_APP: Notification shown in the application (stored in database)
 * - EMAIL: Notification sent via email (SMTP)
 * - PUSH: Push notification sent to mobile device (FCM/APNs)
 * - SMS: SMS notification (future, requires Twilio/SNS)
 */
@Getter
public enum NotificationChannel {
    IN_APP("In-App", "Notification displayed within the application", true),
    EMAIL("Email", "Notification sent via email", true),
    PUSH("Push", "Push notification to mobile device", false),
    SMS("SMS", "SMS text message", false);

    private final String displayName;
    private final String description;
    private final boolean enabled;

    NotificationChannel(String displayName, String description, boolean enabled) {
        this.displayName = displayName;
        this.description = description;
        this.enabled = enabled;
    }
}

