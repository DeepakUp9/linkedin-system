package com.linkedin.notification.service;

import com.linkedin.common.exceptions.ResourceNotFoundException;
import com.linkedin.notification.dto.MarkAsReadRequestDto;
import com.linkedin.notification.dto.NotificationResponseDto;
import com.linkedin.notification.dto.NotificationStatsDto;
import com.linkedin.notification.mapper.NotificationMapper;
import com.linkedin.notification.model.Notification;
import com.linkedin.notification.model.NotificationChannel;
import com.linkedin.notification.model.NotificationStatus;
import com.linkedin.notification.model.NotificationType;
import com.linkedin.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Core service for managing notifications.
 * 
 * Responsibilities:
 * 1. Create notifications (in-app, email, push)
 * 2. Deliver notifications via appropriate channels
 * 3. Mark notifications as read/unread
 * 4. Delete notifications
 * 5. Get notification statistics
 * 6. Cleanup old notifications
 * 
 * Architecture:
 * <pre>
 * Kafka Consumer (ConnectionEventConsumer)
 *       ↓
 * NotificationService.createNotification()
 *       ↓
 * Check user preferences
 *       ↓
 * Create notification records (one per channel)
 *       ↓
 * Strategy Pattern: Deliver via channels
 *       ├─→ InAppDeliveryStrategy (save to DB)
 *       ├─→ EmailDeliveryStrategy (send email)
 *       └─→ PushDeliveryStrategy (send push)
 * </pre>
 * 
 * Example Flow:
 * <pre>
 * Event: "User A accepted connection with User B"
 *   ↓
 * createConnectionAcceptedNotification(userB, userA)
 *   ↓
 * Check preferences: userB wants EMAIL + IN_APP
 *   ↓
 * Create 2 notification records:
 *   1. channel=IN_APP, status=PENDING
 *   2. channel=EMAIL, status=PENDING
 *   ↓
 * Deliver:
 *   - IN_APP: Save to DB, status=DELIVERED
 *   - EMAIL: Send email, status=SENT
 *   ↓
 * User sees notification in app ✅
 * User receives email ✅
 * </pre>
 * 
 * @see Notification
 * @see NotificationResponseDto
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;
    private final NotificationMapper mapper;
    private final com.linkedin.notification.strategy.DeliveryStrategyFactory deliveryStrategyFactory;

    // =========================================================================
    // Read Operations (Queries)
    // =========================================================================

    /**
     * Get all notifications for a user with pagination.
     * 
     * Use Case:
     * User opens notification center, sees paginated list
     * 
     * Performance:
     * - Pagination prevents loading 1000s of notifications at once
     * - Results cached in Redis
     * 
     * @param userId The user ID
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of notifications
     */
    @Cacheable(value = "userNotifications", key = "#userId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<NotificationResponseDto> getNotifications(Long userId, Pageable pageable) {
        log.debug("Fetching notifications for user {} with pagination: {}", userId, pageable);
        
        Page<Notification> notifications = notificationRepository.findByUserId(userId, pageable);
        
        log.info("Retrieved {} notifications for user {} (page {} of {})", 
            notifications.getNumberOfElements(), userId, 
            notifications.getNumber() + 1, notifications.getTotalPages());
        
        return notifications.map(mapper::toDto);
    }

    /**
     * Get unread in-app notifications for a user.
     * 
     * Use Case:
     * Show unread notifications in dropdown menu
     * 
     * @param userId The user ID
     * @return List of unread notifications
     */
    @Cacheable(value = "unreadNotifications", key = "#userId")
    public List<NotificationResponseDto> getUnreadNotifications(Long userId) {
        log.debug("Fetching unread notifications for user {}", userId);
        
        List<Notification> notifications = notificationRepository
            .findByUserIdAndChannelAndIsReadOrderByCreatedAtDesc(
                userId, NotificationChannel.IN_APP, false
            );
        
        log.info("Retrieved {} unread notifications for user {}", notifications.size(), userId);
        return mapper.toDtoList(notifications);
    }

    /**
     * Get notification statistics for a user.
     * 
     * Use Case:
     * Display unread count badge: 🔔 5
     * 
     * Performance:
     * - Cached in Redis (TTL: 5 minutes)
     * - Fast queries (use indexes)
     * 
     * @param userId The user ID
     * @return Statistics (total, unread, failed counts)
     */
    @Cacheable(value = "notificationStats", key = "#userId")
    public NotificationStatsDto getNotificationStats(Long userId) {
        log.debug("Fetching notification statistics for user {}", userId);
        
        // Use repository's aggregation query
        Object[] stats = notificationRepository.getNotificationStats(userId);
        
        long totalCount = stats[0] != null ? ((Number) stats[0]).longValue() : 0L;
        long unreadCount = stats[1] != null ? ((Number) stats[1]).longValue() : 0L;
        long failedCount = stats[2] != null ? ((Number) stats[2]).longValue() : 0L;
        long readCount = totalCount - unreadCount;
        
        NotificationStatsDto statsDto = NotificationStatsDto.builder()
            .totalNotifications(totalCount)
            .unreadCount(unreadCount)
            .readCount(readCount)
            .failedCount(failedCount)
            .build();
        
        log.info("Stats for user {}: total={}, unread={}, failed={}", 
            userId, totalCount, unreadCount, failedCount);
        
        return statsDto;
    }

    /**
     * Get a single notification by ID.
     * 
     * Security:
     * - Verifies notification belongs to user
     * - Throws exception if not found or unauthorized
     * 
     * @param notificationId The notification ID
     * @param userId The user ID (for authorization)
     * @return Notification DTO
     * @throws ResourceNotFoundException if not found or unauthorized
     */
    public NotificationResponseDto getNotificationById(Long notificationId, Long userId) {
        log.debug("Fetching notification {} for user {}", notificationId, userId);
        
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> {
                log.warn("Notification {} not found", notificationId);
                return new ResourceNotFoundException(
                    "Notification not found", "NOTIFICATION_NOT_FOUND"
                );
            });
        
        // Authorization check
        if (!notification.getUserId().equals(userId)) {
            log.warn("User {} attempted to access notification {} belonging to user {}", 
                userId, notificationId, notification.getUserId());
            throw new ResourceNotFoundException(
                "Notification not found", "NOTIFICATION_NOT_FOUND"
            );
        }
        
        return mapper.toDto(notification);
    }

    // =========================================================================
    // Write Operations (Commands)
    // =========================================================================

    /**
     * Mark a single notification as read.
     * 
     * Business Rules:
     * - Only IN_APP notifications can be marked as read
     * - Email/Push notifications don't have read status
     * 
     * Side Effects:
     * - Sets isRead = true
     * - Sets readAt = now
     * - Updates status to READ
     * - Invalidates caches
     * 
     * @param notificationId The notification ID
     * @param userId The user ID (for authorization)
     * @return Updated notification
     * @throws ResourceNotFoundException if not found or unauthorized
     */
    @Transactional
    @CacheEvict(value = {"userNotifications", "unreadNotifications", "notificationStats"}, 
                key = "#userId", allEntries = true)
    public NotificationResponseDto markAsRead(Long notificationId, Long userId) {
        log.info("Marking notification {} as read for user {}", notificationId, userId);
        
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Notification not found", "NOTIFICATION_NOT_FOUND"
            ));
        
        // Authorization check
        if (!notification.getUserId().equals(userId)) {
            throw new ResourceNotFoundException(
                "Notification not found", "NOTIFICATION_NOT_FOUND"
            );
        }
        
        // Business rule: Only IN_APP notifications can be marked as read
        if (!notification.isInApp()) {
            log.warn("Attempted to mark non-in-app notification {} as read", notificationId);
            throw new com.linkedin.common.exceptions.ValidationException(
                "Only in-app notifications can be marked as read", 
                "INVALID_NOTIFICATION_TYPE"
            );
        }
        
        // Mark as read
        notification.markAsRead();
        Notification updated = notificationRepository.save(notification);
        
        log.info("Successfully marked notification {} as read", notificationId);
        return mapper.toDto(updated);
    }

    /**
     * Mark multiple notifications as read (batch operation).
     * 
     * Use Case:
     * User clicks "Mark selected as read" with 10 notifications selected
     * 
     * Performance:
     * - Single transaction
     * - Batch save
     * - Single cache invalidation
     * 
     * @param request Contains list of notification IDs
     * @param userId The user ID (for authorization)
     * @return Number of notifications marked as read
     */
    @Transactional
    @CacheEvict(value = {"userNotifications", "unreadNotifications", "notificationStats"}, 
                key = "#userId", allEntries = true)
    public int markAsRead(MarkAsReadRequestDto request, Long userId) {
        log.info("Batch marking {} notifications as read for user {}", 
            request.getNotificationIds().size(), userId);
        
        int markedCount = 0;
        
        for (Long notificationId : request.getNotificationIds()) {
            try {
                Notification notification = notificationRepository.findById(notificationId)
                    .orElse(null);
                
                if (notification != null && 
                    notification.getUserId().equals(userId) && 
                    notification.isInApp()) {
                    
                    notification.markAsRead();
                    notificationRepository.save(notification);
                    markedCount++;
                }
            } catch (Exception e) {
                log.warn("Failed to mark notification {} as read: {}", 
                    notificationId, e.getMessage());
                // Continue with other notifications
            }
        }
        
        log.info("Successfully marked {} out of {} notifications as read for user {}", 
            markedCount, request.getNotificationIds().size(), userId);
        
        return markedCount;
    }

    /**
     * Mark ALL in-app notifications as read for a user.
     * 
     * Use Case:
     * User clicks "Mark all as read" button
     * 
     * Performance:
     * - Single UPDATE query (no loading into memory)
     * - Very fast even with 1000s of notifications
     * 
     * @param userId The user ID
     * @return Number of notifications marked as read
     */
    @Transactional
    @CacheEvict(value = {"userNotifications", "unreadNotifications", "notificationStats"}, 
                key = "#userId", allEntries = true)
    public int markAllAsRead(Long userId) {
        log.info("Marking all notifications as read for user {}", userId);
        
        int count = notificationRepository.markAllAsRead(
            userId, NotificationChannel.IN_APP, false
        );
        
        log.info("Marked {} notifications as read for user {}", count, userId);
        return count;
    }

    /**
     * Delete a notification.
     * 
     * Use Case:
     * User dismisses notification
     * 
     * @param notificationId The notification ID
     * @param userId The user ID (for authorization)
     */
    @Transactional
    @CacheEvict(value = {"userNotifications", "unreadNotifications", "notificationStats"}, 
                key = "#userId", allEntries = true)
    public void deleteNotification(Long notificationId, Long userId) {
        log.info("Deleting notification {} for user {}", notificationId, userId);
        
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Notification not found", "NOTIFICATION_NOT_FOUND"
            ));
        
        // Authorization check
        if (!notification.getUserId().equals(userId)) {
            throw new ResourceNotFoundException(
                "Notification not found", "NOTIFICATION_NOT_FOUND"
            );
        }
        
        notificationRepository.delete(notification);
        log.info("Successfully deleted notification {}", notificationId);
    }

    /**
     * Delete all read notifications for a user.
     * 
     * Use Case:
     * User clicks "Clear all read notifications"
     * 
     * @param userId The user ID
     * @return Number of notifications deleted
     */
    @Transactional
    @CacheEvict(value = {"userNotifications", "unreadNotifications", "notificationStats"}, 
                key = "#userId", allEntries = true)
    public int deleteAllRead(Long userId) {
        log.info("Deleting all read notifications for user {}", userId);
        
        List<Notification> readNotifications = notificationRepository
            .findByUserIdAndChannelAndIsReadOrderByCreatedAtDesc(
                userId, NotificationChannel.IN_APP, true
            );
        
        int count = readNotifications.size();
        notificationRepository.deleteAll(readNotifications);
        
        log.info("Deleted {} read notifications for user {}", count, userId);
        return count;
    }

    // =========================================================================
    // Notification Creation (Called by Kafka Consumers)
    // =========================================================================

    /**
     * Create a new notification.
     * 
     * This is the main entry point for creating notifications.
     * Called by Kafka consumers when events are received.
     * 
     * Process:
     * 1. Check user preferences (which channels?)
     * 2. Create notification record for each enabled channel
     * 3. Deliver notification via Strategy Pattern
     * 
     * Note: Actual delivery is handled by Strategy Pattern (next step)
     * For now, we just create the notification records.
     * 
     * @param userId Recipient user ID
     * @param type Notification type
     * @param title Notification title
     * @param message Notification message
     * @param actionLink Optional action link
     * @param relatedEntityType Optional related entity type
     * @param relatedEntityId Optional related entity ID
     */
    @Transactional
    @CacheEvict(value = {"userNotifications", "unreadNotifications", "notificationStats"}, 
                key = "#userId", allEntries = true)
    public void createNotification(
        Long userId,
        NotificationType type,
        String title,
        String message,
        String actionLink,
        String relatedEntityType,
        String relatedEntityId
    ) {
        log.info("Creating {} notification for user {}", type, userId);
        
        // Check which channels user wants for this notification type
        boolean emailEnabled = preferenceService.isChannelEnabled(
            userId, type, NotificationChannel.EMAIL
        );
        boolean inAppEnabled = preferenceService.isChannelEnabled(
            userId, type, NotificationChannel.IN_APP
        );
        boolean pushEnabled = preferenceService.isChannelEnabled(
            userId, type, NotificationChannel.PUSH
        );
        
        log.debug("User {} preferences for {}: email={}, inApp={}, push={}", 
            userId, type, emailEnabled, inAppEnabled, pushEnabled);
        
        // Create notification for each enabled channel
        if (inAppEnabled) {
            createNotificationForChannel(
                userId, type, title, message, actionLink,
                relatedEntityType, relatedEntityId, NotificationChannel.IN_APP
            );
        }
        
        if (emailEnabled) {
            createNotificationForChannel(
                userId, type, title, message, actionLink,
                relatedEntityType, relatedEntityId, NotificationChannel.EMAIL
            );
        }
        
        if (pushEnabled) {
            createNotificationForChannel(
                userId, type, title, message, actionLink,
                relatedEntityType, relatedEntityId, NotificationChannel.PUSH
            );
        }
        
        log.info("Created {} notification(s) for user {}", 
            (inAppEnabled ? 1 : 0) + (emailEnabled ? 1 : 0) + (pushEnabled ? 1 : 0), userId);
    }

    /**
     * Create notification record for a specific channel.
     * 
     * @param userId Recipient
     * @param type Notification type
     * @param title Title
     * @param message Message
     * @param actionLink Action link
     * @param relatedEntityType Related entity type
     * @param relatedEntityId Related entity ID
     * @param channel Delivery channel
     */
    private void createNotificationForChannel(
        Long userId,
        NotificationType type,
        String title,
        String message,
        String actionLink,
        String relatedEntityType,
        String relatedEntityId,
        NotificationChannel channel
    ) {
        Notification notification = Notification.builder()
            .userId(userId)
            .type(type)
            .channel(channel)
            .status(NotificationStatus.PENDING)
            .title(title)
            .message(message)
            .actionLink(actionLink)
            .relatedEntityType(relatedEntityType)
            .relatedEntityId(relatedEntityId)
            .build();
        
        Notification saved = notificationRepository.save(notification);
        log.debug("Created {} notification with ID {} for user {}", 
            channel, saved.getId(), userId);
        
        // Trigger delivery via Strategy Pattern
        com.linkedin.notification.strategy.DeliveryStrategy strategy = 
            deliveryStrategyFactory.getStrategy(channel);
        
        if (strategy != null) {
            log.debug("Triggering delivery for notification {} via {}", 
                saved.getId(), strategy.getClass().getSimpleName());
            strategy.deliver(saved);
        } else {
            log.warn("No delivery strategy found for channel: {}. Marking as failed.", channel);
            saved.markAsFailed("No delivery strategy available for channel: " + channel);
            notificationRepository.save(saved);
        }
    }

    // =========================================================================
    // Scheduled Tasks (Cleanup)
    // =========================================================================

    /**
     * Cleanup old read notifications.
     * 
     * Schedule:
     * - Runs daily at 2 AM
     * - Deletes read notifications older than 90 days
     * 
     * Why?
     * - Database grows large over time
     * - Old read notifications not useful
     * - Improves query performance
     * 
     * Configuration:
     * app.notification.cleanup-old-read-days: 90 (in application.yml)
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    @Transactional
    public void cleanupOldReadNotifications() {
        log.info("Starting cleanup of old read notifications");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
        int deleted = notificationRepository.deleteOldReadNotifications(true, cutoffDate);
        
        log.info("Cleanup complete: Deleted {} old read notifications", deleted);
    }
}

