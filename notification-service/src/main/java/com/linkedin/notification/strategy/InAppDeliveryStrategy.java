package com.linkedin.notification.strategy;

import com.linkedin.notification.model.Notification;
import com.linkedin.notification.model.NotificationChannel;
import com.linkedin.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy for delivering in-app notifications.
 * 
 * Purpose:
 * In-app notifications are displayed within the application (notification center, dropdown).
 * 
 * How It Works:
 * <pre>
 * 1. Notification already saved to database (by NotificationService)
 * 2. This strategy marks it as "delivered"
 * 3. User sees it in notification center
 * 4. Done! ✅
 * </pre>
 * 
 * Characteristics:
 * - Instant delivery (no external dependencies)
 * - Highest priority (priority = 1)
 * - Highest reliability (no network calls)
 * - Persistent (stored in database)
 * 
 * User Experience:
 * <pre>
 * User opens notification dropdown:
 *   ↓
 * API call: GET /api/notifications
 *   ↓
 * Returns all IN_APP notifications with status=DELIVERED
 *   ↓
 * User sees: "John Doe accepted your connection request" 🔔
 * </pre>
 * 
 * Design Pattern: Strategy Pattern (Concrete Strategy)
 * 
 * @see DeliveryStrategy
 * @see Notification
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InAppDeliveryStrategy implements DeliveryStrategy {

    private final NotificationRepository notificationRepository;

    /**
     * Deliver notification by marking it as delivered in database.
     * 
     * Process:
     * 1. Notification already exists in database (created by NotificationService)
     * 2. Update status: PENDING → DELIVERED
     * 3. User can now see it via API
     * 
     * Why So Simple?
     * - In-app notifications ARE database records
     * - "Delivery" = making it visible/queryable
     * - No external calls needed
     * 
     * Error Handling:
     * - Should never fail (local database operation)
     * - If fails, indicates serious system issue
     * - Logs error and marks notification as failed
     * 
     * @param notification The notification to deliver
     */
    @Override
    public void deliver(Notification notification) {
        try {
            log.debug("Delivering in-app notification {} for user {}", 
                notification.getId(), notification.getUserId());
            
            // Validate
            if (!canHandle(notification)) {
                log.warn("InAppDeliveryStrategy cannot handle notification {} with channel {}", 
                    notification.getId(), notification.getChannel());
                notification.markAsFailed("Wrong channel for in-app delivery");
                notificationRepository.save(notification);
                return;
            }
            
            // Mark as delivered
            notification.markAsDelivered();
            notificationRepository.save(notification);
            
            log.info("Successfully delivered in-app notification {} for user {}", 
                notification.getId(), notification.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to deliver in-app notification {}: {}", 
                notification.getId(), e.getMessage(), e);
            
            // Mark as failed
            notification.markAsFailed("In-app delivery failed: " + e.getMessage());
            notificationRepository.save(notification);
        }
    }

    /**
     * This strategy supports IN_APP channel.
     * 
     * @return NotificationChannel.IN_APP
     */
    @Override
    public NotificationChannel getSupportedChannel() {
        return NotificationChannel.IN_APP;
    }

    /**
     * In-app has highest priority (instant, reliable).
     * 
     * @return 1 (highest priority)
     */
    @Override
    public int getPriority() {
        return 1; // Highest priority
    }

    /**
     * Additional validation for in-app notifications.
     * 
     * Checks:
     * - Notification is not null
     * - Channel is IN_APP
     * - User ID is present
     * 
     * @param notification The notification to validate
     * @return true if valid and can be delivered
     */
    @Override
    public boolean canHandle(Notification notification) {
        if (!DeliveryStrategy.super.canHandle(notification)) {
            return false;
        }
        
        // Additional validation
        if (notification.getUserId() == null) {
            log.warn("Cannot deliver in-app notification without user ID");
            return false;
        }
        
        return true;
    }
}

