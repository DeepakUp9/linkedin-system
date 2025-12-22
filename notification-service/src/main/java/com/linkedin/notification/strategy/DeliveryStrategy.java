package com.linkedin.notification.strategy;

import com.linkedin.notification.model.Notification;
import com.linkedin.notification.model.NotificationChannel;

/**
 * Strategy Pattern interface for notification delivery.
 * 
 * Purpose:
 * - Defines contract for all delivery strategies
 * - Allows different delivery methods to be used interchangeably
 * - Promotes Open/Closed Principle (open for extension, closed for modification)
 * 
 * Design Pattern: Strategy Pattern
 * 
 * Benefits:
 * 1. Single Responsibility: Each strategy handles one delivery method
 * 2. Open/Closed: Add new channels without modifying existing code
 * 3. Testability: Each strategy can be tested independently
 * 4. Flexibility: Switch delivery methods at runtime
 * 
 * How to Use:
 * <pre>
 * {@code
 * // Get appropriate strategy for notification channel
 * DeliveryStrategy strategy = strategyFactory.getStrategy(notification.getChannel());
 * 
 * // Deliver using that strategy
 * strategy.deliver(notification);
 * 
 * // Different notifications can use different strategies
 * // Strategy selected at runtime based on channel
 * }
 * </pre>
 * 
 * Example Implementations:
 * - InAppDeliveryStrategy: Saves to database, marks as delivered
 * - EmailDeliveryStrategy: Renders template, sends via SMTP
 * - PushDeliveryStrategy: Sends to FCM/APNs
 * - SmsDeliveryStrategy: Sends via Twilio/AWS SNS (future)
 * 
 * Adding New Channel:
 * <pre>
 * 1. Create new enum value in NotificationChannel
 * 2. Implement DeliveryStrategy interface
 * 3. Register in DeliveryStrategyFactory
 * 4. Done! No changes to existing code needed ✅
 * </pre>
 * 
 * @see Notification
 * @see NotificationChannel
 * @see InAppDeliveryStrategy
 * @see EmailDeliveryStrategy
 * @see PushDeliveryStrategy
 */
public interface DeliveryStrategy {

    /**
     * Deliver a notification using this strategy's specific method.
     * 
     * Responsibilities:
     * 1. Perform actual delivery (send email, save to DB, send push, etc.)
     * 2. Update notification status (SENT, DELIVERED, FAILED)
     * 3. Handle errors gracefully
     * 4. Log delivery attempts
     * 
     * Status Transitions:
     * - Success: PENDING → SENT → DELIVERED
     * - Failure: PENDING → FAILED
     * 
     * Error Handling:
     * - Should NOT throw exceptions (async, no one to catch)
     * - Should update notification with error message
     * - Should log for monitoring/alerting
     * 
     * Example Implementation:
     * <pre>
     * {@code
     * public void deliver(Notification notification) {
     *     try {
     *         // Perform delivery
     *         sendEmail(notification);
     *         
     *         // Update status
     *         notification.markAsSent();
     *         notification.markAsDelivered();
     *         repository.save(notification);
     *         
     *         log.info("Successfully delivered notification {}", notification.getId());
     *     } catch (Exception e) {
     *         // Handle failure
     *         notification.markAsFailed(e.getMessage());
     *         repository.save(notification);
     *         
     *         log.error("Failed to deliver notification {}: {}", 
     *             notification.getId(), e.getMessage());
     *     }
     * }
     * }
     * </pre>
     * 
     * @param notification The notification to deliver
     */
    void deliver(Notification notification);

    /**
     * Get the channel this strategy supports.
     * 
     * Used by DeliveryStrategyFactory to map channels to strategies.
     * 
     * @return The notification channel this strategy handles
     */
    NotificationChannel getSupportedChannel();

    /**
     * Check if this strategy can handle the given notification.
     * 
     * Default implementation checks if notification's channel matches supported channel.
     * Can be overridden for more complex logic (e.g., check if email address valid).
     * 
     * @param notification The notification to check
     * @return true if this strategy can handle the notification
     */
    default boolean canHandle(Notification notification) {
        return notification != null && 
               notification.getChannel() == getSupportedChannel();
    }

    /**
     * Get priority for this delivery strategy.
     * 
     * Lower number = higher priority.
     * Used when multiple strategies can handle a notification.
     * 
     * Priority Guidelines:
     * - IN_APP: 1 (highest - instant, no external dependencies)
     * - PUSH: 2 (high - real-time)
     * - EMAIL: 3 (medium - may be delayed)
     * - SMS: 4 (low - expensive, use sparingly)
     * 
     * @return Priority value (lower = higher priority)
     */
    default int getPriority() {
        return 10; // Default medium priority
    }
}

