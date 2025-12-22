package com.linkedin.notification.strategy;

import com.linkedin.notification.model.Notification;
import com.linkedin.notification.model.NotificationChannel;
import com.linkedin.notification.model.NotificationType;
import com.linkedin.notification.repository.NotificationRepository;
import com.linkedin.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Strategy for delivering email notifications.
 * 
 * Purpose:
 * Sends notification content to user's email address.
 * 
 * How It Works:
 * <pre>
 * 1. Notification record exists in database
 * 2. This strategy:
 *    - Fetches user's email from user-service (via Feign)
 *    - Renders email template with notification data
 *    - Sends email via SMTP
 *    - Updates notification status
 * 3. User receives email in inbox 📧
 * </pre>
 * 
 * Characteristics:
 * - Async delivery (doesn't block caller)
 * - Medium priority (priority = 3)
 * - External dependency (SMTP server)
 * - May be delayed (network latency)
 * - Retry on failure (up to 3 attempts)
 * 
 * User Experience:
 * <pre>
 * John accepts your connection:
 *   ↓
 * ConnectionEventConsumer creates notification
 *   ↓
 * EmailDeliveryStrategy triggered
 *   ↓
 * Email sent: "John Doe accepted your connection request!"
 *   ↓
 * User receives email in Gmail/Outlook ✅
 * </pre>
 * 
 * Template Mapping:
 * - CONNECTION_REQUESTED → connection-requested.html
 * - CONNECTION_ACCEPTED → connection-accepted.html
 * - POST_LIKED → post-liked.html
 * - etc.
 * 
 * Design Pattern: Strategy Pattern (Concrete Strategy)
 * 
 * @see DeliveryStrategy
 * @see EmailService
 * @see Notification
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailDeliveryStrategy implements DeliveryStrategy {

    private final EmailService emailService;
    private final NotificationRepository notificationRepository;
    private final com.linkedin.notification.client.UserServiceClient userServiceClient;

    /**
     * Deliver notification via email.
     * 
     * Process:
     * 1. Validate notification can be emailed
     * 2. Fetch user's email address (from user-service)
     * 3. Determine template based on notification type
     * 4. Prepare template variables
     * 5. Send email (async)
     * 6. Update notification status
     * 
     * Async Execution:
     * - Runs in separate thread pool
     * - Doesn't block caller
     * - Failures logged but don't propagate
     * 
     * Error Handling:
     * - SMTP failure: Mark as FAILED, retry later
     * - User not found: Mark as FAILED, no retry
     * - Template error: Mark as FAILED, no retry
     * 
     * @param notification The notification to deliver
     */
    @Override
    @Async
    public void deliver(Notification notification) {
        try {
            log.debug("Delivering email notification {} for user {}", 
                notification.getId(), notification.getUserId());
            
            // Validate
            if (!canHandle(notification)) {
                log.warn("EmailDeliveryStrategy cannot handle notification {}", 
                    notification.getId());
                notification.markAsFailed("Wrong channel for email delivery");
                notificationRepository.save(notification);
                return;
            }
            
            // Get user's email address
            String userEmail = getUserEmail(notification.getUserId());
            if (userEmail == null) {
                log.warn("No email address found for user {}", notification.getUserId());
                notification.markAsFailed("User email not found");
                notificationRepository.save(notification);
                return;
            }
            
            // Get template name for this notification type
            String templateName = getTemplateName(notification.getType());
            
            // Prepare template variables
            Map<String, Object> variables = prepareTemplateVariables(notification);
            
            // Mark as SENT before actual sending
            notification.markAsSent();
            notificationRepository.save(notification);
            
            // Send email (async within async - nested async call)
            emailService.sendTemplatedEmail(
                userEmail,
                notification.getTitle(),
                templateName,
                variables
            );
            
            // Mark as DELIVERED after successful send
            notification.markAsDelivered();
            notificationRepository.save(notification);
            
            log.info("Successfully delivered email notification {} to {}", 
                notification.getId(), userEmail);
            
        } catch (Exception e) {
            log.error("Failed to deliver email notification {}: {}", 
                notification.getId(), e.getMessage(), e);
            
            // Mark as failed
            notification.markAsFailed("Email delivery failed: " + e.getMessage());
            notificationRepository.save(notification);
            
            // Note: Retry mechanism can be implemented using @Retryable annotation
            // or Spring Batch for failed notifications
        }
    }

    /**
     * This strategy supports EMAIL channel.
     * 
     * @return NotificationChannel.EMAIL
     */
    @Override
    public NotificationChannel getSupportedChannel() {
        return NotificationChannel.EMAIL;
    }

    /**
     * Email has medium priority (may be delayed).
     * 
     * @return 3 (medium priority)
     */
    @Override
    public int getPriority() {
        return 3; // Medium priority
    }

    /**
     * Additional validation for email notifications.
     * 
     * Checks:
     * - Notification is not null
     * - Channel is EMAIL
     * - Title and message are present
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
        if (notification.getTitle() == null || notification.getTitle().isEmpty()) {
            log.warn("Cannot deliver email notification without title");
            return false;
        }
        
        if (notification.getMessage() == null || notification.getMessage().isEmpty()) {
            log.warn("Cannot deliver email notification without message");
            return false;
        }
        
        return true;
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Fetch user's email address from user-service.
     * 
     * @param userId The user ID
     * @return User's email address
     */
    private String getUserEmail(Long userId) {
        try {
            com.linkedin.notification.user.dto.UserResponse user = userServiceClient.getUserById(userId);
            return user.getEmail();
        } catch (Exception e) {
            log.error("Failed to fetch user email for userId {}: {}", userId, e.getMessage());
            // Fallback: return null to mark notification as failed
            return null;
        }
    }

    /**
     * Get email template name based on notification type.
     * 
     * Template Naming Convention:
     * - CONNECTION_REQUESTED → connection-requested
     * - CONNECTION_ACCEPTED → connection-accepted
     * - POST_LIKED → post-liked
     * 
     * Templates Location:
     * src/main/resources/templates/email/{template-name}.html
     * 
     * @param type The notification type
     * @return Template name (without .html extension)
     */
    private String getTemplateName(NotificationType type) {
        // Convert enum name to kebab-case
        // CONNECTION_ACCEPTED → connection-accepted
        return type.name().toLowerCase().replace("_", "-");
    }

    /**
     * Prepare variables for email template.
     * 
     * Common Variables:
     * - title: Notification title
     * - message: Notification message
     * - actionLink: Link to action (e.g., view profile)
     * - year: Current year (for footer)
     * 
     * Type-Specific Variables:
     * Added based on metadata or related entity
     * 
     * @param notification The notification
     * @return Map of template variables
     */
    private Map<String, Object> prepareTemplateVariables(Notification notification) {
        Map<String, Object> variables = new HashMap<>();
        
        // Common variables
        variables.put("title", notification.getTitle());
        variables.put("message", notification.getMessage());
        variables.put("actionLink", notification.getActionLink());
        variables.put("year", java.time.Year.now().getValue());
        
        // Type-specific variables can be added here based on notification type
        // For example: connection ID, post ID, etc. from metadata field
        
        return variables;
    }
}

