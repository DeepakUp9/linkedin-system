package com.linkedin.notification.repository;

import com.linkedin.notification.model.Notification;
import com.linkedin.notification.model.NotificationChannel;
import com.linkedin.notification.model.NotificationStatus;
import com.linkedin.notification.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA Repository for Notification entities.
 * 
 * Purpose:
 * Provides database access methods for notifications without writing SQL.
 * Spring Data JPA generates implementation automatically based on method names.
 * 
 * How It Works:
 * <pre>
 * 1. You define method signatures
 * 2. Spring parses method names
 * 3. Spring generates SQL queries
 * 4. You call methods, get results
 * </pre>
 * 
 * Example:
 * <pre>
 * {@code
 * // Method signature:
 * List<Notification> findByUserId(Long userId);
 * 
 * // Spring generates:
 * SELECT * FROM notifications WHERE user_id = ?
 * 
 * // Usage:
 * List<Notification> notifications = repository.findByUserId(123L);
 * }
 * </pre>
 * 
 * @see Notification
 * @see org.springframework.data.jpa.repository.JpaRepository
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // =========================================================================
    // Basic Queries - Find by User
    // =========================================================================

    /**
     * Find all notifications for a specific user.
     * 
     * Generated SQL:
     * SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC
     * 
     * Use Case:
     * Display all notifications in user's notification center
     * 
     * @param userId The user ID
     * @return List of notifications, newest first
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find notifications for a user with pagination.
     * 
     * Why Pagination?
     * - User might have 1000+ notifications
     * - Don't load all at once (slow, memory intensive)
     * - Load in pages: 20 at a time
     * 
     * Usage:
     * <pre>
     * {@code
     * Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
     * Page<Notification> page = repository.findByUserId(123L, pageable);
     * 
     * List<Notification> notifications = page.getContent();  // Current page items
     * int totalPages = page.getTotalPages();                // Total pages
     * long totalItems = page.getTotalElements();            // Total items
     * }
     * </pre>
     * 
     * @param userId The user ID
     * @param pageable Pagination parameters (page number, size, sort)
     * @return Page of notifications
     */
    Page<Notification> findByUserId(Long userId, Pageable pageable);

    /**
     * Find unread in-app notifications for a user.
     * 
     * Generated SQL:
     * SELECT * FROM notifications 
     * WHERE user_id = ? 
     *   AND channel = 'IN_APP' 
     *   AND is_read = FALSE 
     * ORDER BY created_at DESC
     * 
     * Use Case:
     * Show red badge with unread count in UI
     * 
     * @param userId The user ID
     * @param channel Notification channel (IN_APP)
     * @param isRead Read status (false for unread)
     * @return List of unread notifications
     */
    List<Notification> findByUserIdAndChannelAndIsReadOrderByCreatedAtDesc(
        Long userId, NotificationChannel channel, boolean isRead);

    /**
     * Find notifications by user and type.
     * 
     * Use Case:
     * "Show me all connection-related notifications"
     * 
     * @param userId The user ID
     * @param type Notification type (e.g., CONNECTION_ACCEPTED)
     * @param pageable Pagination parameters
     * @return Page of notifications
     */
    Page<Notification> findByUserIdAndType(Long userId, NotificationType type, Pageable pageable);

    // =========================================================================
    // Count Queries
    // =========================================================================

    /**
     * Count unread in-app notifications for a user.
     * 
     * Generated SQL:
     * SELECT COUNT(*) FROM notifications 
     * WHERE user_id = ? 
     *   AND channel = 'IN_APP' 
     *   AND is_read = FALSE
     * 
     * Use Case:
     * Display unread count badge in UI (🔔 5)
     * 
     * Performance:
     * - Fast query (uses index: idx_notifications_user_read)
     * - Cached in Redis to avoid repeated DB calls
     * 
     * @param userId The user ID
     * @param channel Notification channel
     * @param isRead Read status
     * @return Count of unread notifications
     */
    long countByUserIdAndChannelAndIsRead(Long userId, NotificationChannel channel, boolean isRead);

    /**
     * Count all notifications for a user.
     * 
     * @param userId The user ID
     * @return Total notification count
     */
    long countByUserId(Long userId);

    // =========================================================================
    // Status-Based Queries
    // =========================================================================

    /**
     * Find notifications by status.
     * 
     * Use Case:
     * - Find FAILED notifications for retry
     * - Find PENDING notifications for batch processing
     * 
     * @param status Notification status
     * @return List of notifications
     */
    List<Notification> findByStatus(NotificationStatus status);

    /**
     * Find failed notifications that can be retried.
     * 
     * Custom Query:
     * Uses @Query annotation for complex logic
     * 
     * @param status Status (FAILED)
     * @param maxRetries Maximum retry attempts
     * @return List of notifications eligible for retry
     */
    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.retryCount < :maxRetries")
    List<Notification> findFailedNotificationsForRetry(
        @Param("status") NotificationStatus status,
        @Param("maxRetries") int maxRetries
    );

    // =========================================================================
    // Bulk Operations
    // =========================================================================

    /**
     * Mark all in-app notifications as read for a user.
     * 
     * Why @Modifying?
     * - This is an UPDATE query, not SELECT
     * - @Modifying tells Spring it modifies data
     * - @Transactional required when calling this method
     * 
     * Generated SQL:
     * UPDATE notifications 
     * SET is_read = TRUE, 
     *     read_at = CURRENT_TIMESTAMP,
     *     status = 'READ'
     * WHERE user_id = ? 
     *   AND channel = 'IN_APP' 
     *   AND is_read = FALSE
     * 
     * Performance:
     * - Single UPDATE query (fast)
     * - Better than loading all, updating each, saving back
     * 
     * @param userId The user ID
     * @param channel Notification channel
     * @param isRead Current read status (false)
     * @return Number of notifications updated
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP, n.status = 'READ' " +
           "WHERE n.userId = :userId AND n.channel = :channel AND n.read = :isRead")
    int markAllAsRead(
        @Param("userId") Long userId,
        @Param("channel") NotificationChannel channel,
        @Param("isRead") boolean isRead
    );

    /**
     * Delete old read notifications.
     * 
     * Use Case:
     * Cleanup job: "Delete read notifications older than 90 days"
     * 
     * Why Delete Old Notifications?
     * - Database grows large over time
     * - Old read notifications not useful
     * - Improve query performance
     * 
     * @param isRead Read status (true)
     * @param beforeDate Cutoff date
     * @return Number of notifications deleted
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.read = :isRead AND n.createdAt < :beforeDate")
    int deleteOldReadNotifications(
        @Param("isRead") boolean isRead,
        @Param("beforeDate") LocalDateTime beforeDate
    );

    /**
     * Delete notifications for a specific user.
     * 
     * Use Case:
     * User account deletion (GDPR compliance)
     * 
     * @param userId The user ID
     * @return Number of notifications deleted
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    // =========================================================================
    // Advanced Queries
    // =========================================================================

    /**
     * Find recent unread notifications (last 7 days).
     * 
     * Use Case:
     * Show only recent unread notifications, hide old ones
     * 
     * @param userId The user ID
     * @param channel Notification channel
     * @param isRead Read status
     * @param afterDate Date cutoff (e.g., 7 days ago)
     * @return List of recent unread notifications
     */
    @Query("SELECT n FROM Notification n " +
           "WHERE n.userId = :userId " +
           "AND n.channel = :channel " +
           "AND n.read = :isRead " +
           "AND n.createdAt > :afterDate " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findRecentUnreadNotifications(
        @Param("userId") Long userId,
        @Param("channel") NotificationChannel channel,
        @Param("isRead") boolean isRead,
        @Param("afterDate") LocalDateTime afterDate
    );

    /**
     * Find notifications by related entity.
     * 
     * Use Case:
     * "Find all notifications about connection ID 123"
     * When user views connection, show related notifications
     * 
     * @param relatedEntityType Entity type (e.g., "CONNECTION")
     * @param relatedEntityId Entity ID (e.g., "123")
     * @return List of related notifications
     */
    List<Notification> findByRelatedEntityTypeAndRelatedEntityId(
        String relatedEntityType, String relatedEntityId);

    // =========================================================================
    // Statistics Queries
    // =========================================================================

    /**
     * Get notification statistics for a user.
     * 
     * Custom projection query that returns aggregated data.
     * 
     * Use Case:
     * Dashboard: "You have 50 unread, 200 total, 5 failed"
     * 
     * @param userId The user ID
     * @return Array: [total, unread, failed]
     */
    @Query("SELECT " +
           "COUNT(n), " +
           "SUM(CASE WHEN n.read = false AND n.channel = 'IN_APP' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN n.status = 'FAILED' THEN 1 ELSE 0 END) " +
           "FROM Notification n WHERE n.userId = :userId")
    Object[] getNotificationStats(@Param("userId") Long userId);
}

