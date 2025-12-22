package com.linkedin.notification.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA Entity representing user notification preferences.
 * 
 * Stores which notification types and channels each user wants to receive.
 * 
 * Example:
 * User 123 preferences:
 * - CONNECTION_ACCEPTED: EMAIL=true, IN_APP=true, PUSH=false
 * - POST_LIKED: EMAIL=false, IN_APP=true, PUSH=false
 * 
 * Design:
 * - One record per user per notification type
 * - Boolean flags for each channel
 * - Default values set for all types on user registration
 */
@Entity
@Table(name = "notification_preferences", uniqueConstraints = {
    @UniqueConstraint(name = "uk_prefs_user_type", columnNames = {"user_id", "notification_type"})
}, indexes = {
    @Index(name = "idx_prefs_user", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private boolean emailEnabled = true;

    @Column(name = "in_app_enabled", nullable = false)
    @Builder.Default
    private boolean inAppEnabled = true;

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private boolean pushEnabled = false;

    @Column(name = "sms_enabled", nullable = false)
    @Builder.Default
    private boolean smsEnabled = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Check if ANY channel is enabled for this notification type
     */
    public boolean isAnyChannelEnabled() {
        return emailEnabled || inAppEnabled || pushEnabled || smsEnabled;
    }

    /**
     * Check if specific channel is enabled
     */
    public boolean isChannelEnabled(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailEnabled;
            case IN_APP -> inAppEnabled;
            case PUSH -> pushEnabled;
            case SMS -> smsEnabled;
        };
    }
}

