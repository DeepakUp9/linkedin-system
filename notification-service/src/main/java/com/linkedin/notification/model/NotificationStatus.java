package com.linkedin.notification.model;

import lombok.Getter;

/**
 * Enum representing the delivery status of a notification.
 * 
 * Lifecycle:
 * PENDING → SENT → DELIVERED / FAILED
 * or
 * PENDING → FAILED (immediate failure)
 */
@Getter
public enum NotificationStatus {
    PENDING("Pending", "Notification queued for delivery"),
    SENT("Sent", "Notification sent to delivery channel"),
    DELIVERED("Delivered", "Notification successfully delivered"),
    FAILED("Failed", "Notification delivery failed"),
    READ("Read", "Notification read by user (in-app only)");

    private final String displayName;
    private final String description;

    NotificationStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED || this == READ;
    }
}

