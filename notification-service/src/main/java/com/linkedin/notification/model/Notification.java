package com.linkedin.notification.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA Entity representing a notification.
 * 
 * This entity stores all notifications sent to users through various channels.
 * Each notification is linked to a specific user and contains:
 * - Type (what kind of event triggered it)
 * - Channel (how it was delivered: in-app, email, push)
 * - Status (pending, sent, delivered, read)
 * - Content (title, message, action link)
 * - Metadata (related entity IDs, timestamps)
 * 
 * Design:
 * - One record per notification per channel
 * - If user prefers both email and in-app, creates 2 records
 * - In-app notifications support read/unread status
 * - Email/Push notifications track delivery status
 * 
 * Example Records:
 * <pre>
 * ID  UserId  Type                    Channel  Status     Title
 * 1   123     CONNECTION_ACCEPTED     IN_APP   PENDING    "Jane accepted..."
 * 2   123     CONNECTION_ACCEPTED     EMAIL    SENT       "Jane accepted..."
 * </pre>
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_user", columnList = "user_id"),
    @Index(name = "idx_notifications_user_status", columnList = "user_id, status"),
    @Index(name = "idx_notifications_user_read", columnList = "user_id, is_read"),
    @Index(name = "idx_notifications_created", columnList = "created_at"),
    @Index(name = "idx_notifications_type", columnList = "type")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID of the user who receives this notification.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Type of notification (CONNECTION_ACCEPTED, POST_LIKED, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    /**
     * Delivery channel (IN_APP, EMAIL, PUSH)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    /**
     * Delivery status (PENDING, SENT, DELIVERED, FAILED, READ)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    /**
     * Title of the notification (e.g., "New Connection Request")
     */
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /**
     * Main message content
     */
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * Action link (URL to navigate to when clicked)
     * Example: "/profile/123", "/posts/456"
     */
    @Column(name = "action_link", length = 500)
    private String actionLink;

    /**
     * Icon or image URL to display with notification
     */
    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    /**
     * Whether the notification has been read (IN_APP channel only)
     */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    /**
     * When the notification was read (IN_APP channel only)
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * ID of the related entity (e.g., connectionId, postId)
     * Stored as string for flexibility
     */
    @Column(name = "related_entity_id", length = 100)
    private String relatedEntityId;

    /**
     * Type of the related entity (e.g., "CONNECTION", "POST")
     */
    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

    /**
     * Additional metadata as JSON (flexible storage)
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * When the notification was sent/delivered
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /**
     * Number of retry attempts (for failed deliveries)
     */
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Last error message (if delivery failed)
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * When the entity was created (audit field)
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When the entity was last updated (audit field)
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // =========================================================================
    // Business Logic Methods
    // =========================================================================

    /**
     * Mark notification as read (IN_APP channel only)
     */
    public void markAsRead() {
        if (this.channel == NotificationChannel.IN_APP) {
            this.read = true;
            this.readAt = LocalDateTime.now();
            this.status = NotificationStatus.READ;
        }
    }

    /**
     * Mark notification as sent
     */
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * Mark notification as delivered
     */
    public void markAsDelivered() {
        this.status = NotificationStatus.DELIVERED;
    }

    /**
     * Mark notification as failed
     */
    public void markAsFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }

    /**
     * Check if notification can be retried
     */
    public boolean canRetry(int maxRetries) {
        return this.status == NotificationStatus.FAILED && this.retryCount < maxRetries;
    }

    /**
     * Check if this is an in-app notification
     */
    public boolean isInApp() {
        return this.channel == NotificationChannel.IN_APP;
    }

    /**
     * Check if this is an email notification
     */
    public boolean isEmail() {
        return this.channel == NotificationChannel.EMAIL;
    }
}

