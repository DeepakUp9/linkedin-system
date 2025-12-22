package com.linkedin.notification.consumer;

import com.linkedin.notification.event.*;
import com.linkedin.notification.model.NotificationType;
import com.linkedin.notification.service.NotificationService;
import com.linkedin.notification.strategy.DeliveryStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for connection-related events.
 * 
 * Purpose:
 * Listens to Kafka topics published by connection-service and creates notifications.
 * 
 * Design Pattern: Observer Pattern
 * - Connection Service = Subject (publishes events)
 * - Notification Service = Observer (reacts to events)
 * - Kafka = Event Bus (delivers events)
 * 
 * Architecture:
 * <pre>
 * Connection Service:
 *   User A accepts connection with User B
 *        ↓
 *   Publishes ConnectionAcceptedEvent to Kafka
 *        ↓
 * Kafka Topic: "connection-accepted"
 *        ↓
 * THIS CONSUMER (ConnectionEventConsumer):
 *   @KafkaListener receives event
 *        ↓
 *   Determines recipient (User A should be notified)
 *        ↓
 *   Calls NotificationService.createNotification()
 *        ↓
 *   NotificationService checks preferences
 *        ↓
 *   Creates notification records (IN_APP, EMAIL)
 *        ↓
 *   DeliveryStrategyFactory delivers notifications
 *        ↓
 *   User A sees: "User B accepted your connection request!" ✅
 * </pre>
 * 
 * Topics Consumed:
 * 1. connection-requested
 * 2. connection-accepted
 * 3. connection-rejected
 * 4. connection-blocked
 * 5. connection-cancelled
 * 6. connection-removed
 * 
 * Configuration:
 * - Consumer Group: notification-service-group (in application.yml)
 * - Auto Offset Reset: earliest (process all events from beginning)
 * - Acknowledgment Mode: manual (we control when message is marked as processed)
 * - Concurrency: 3 (3 threads processing messages in parallel)
 * 
 * Error Handling:
 * - Try-catch around each event processing
 * - Log errors but don't throw (prevents message redelivery loop)
 * - Failed events logged for manual investigation
 * 
 * @see ConnectionAcceptedEvent
 * @see NotificationService
 * @see DeliveryStrategyFactory
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConnectionEventConsumer {

    private final NotificationService notificationService;
    private final DeliveryStrategyFactory deliveryStrategyFactory;
    private final com.linkedin.notification.client.UserServiceClient userServiceClient;

    // =========================================================================
    // Kafka Listeners (One per Topic)
    // =========================================================================

    /**
     * Listen to connection-requested events.
     * 
     * Triggered When:
     * User A sends connection request to User B
     * 
     * Notification Created:
     * - Recipient: User B (addresseeId)
     * - Type: CONNECTION_REQUESTED
     * - Message: "User A wants to connect with you"
     * - Action: Link to accept/reject request
     * 
     * @KafkaListener Explanation:
     * - topics: Which Kafka topic to listen to
     * - groupId: Consumer group (for load balancing)
     * - containerFactory: Bean name for listener config (optional)
     * 
     * @Payload: The deserialized event object
     * @Header: Kafka message metadata (partition, offset, etc.)
     * Acknowledgment: Manual acknowledgment control
     * 
     * How It Works:
     * <pre>
     * 1. Kafka delivers message to this method
     * 2. Spring deserializes JSON → ConnectionRequestedEvent object
     * 3. We process event (create notification)
     * 4. We call ack.acknowledge() to mark message as processed
     * 5. Kafka commits offset (won't redeliver this message)
     * </pre>
     * 
     * @param event The connection requested event
     * @param partition Kafka partition this message came from
     * @param offset Kafka offset of this message
     * @param ack Manual acknowledgment
     */
    @KafkaListener(
        topics = "${app.kafka.topics.connection-requested}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleConnectionRequested(
        @Payload ConnectionRequestedEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment ack
    ) {
        log.info("Received CONNECTION_REQUESTED event: connectionId={}, requester={}, addressee={}, partition={}, offset={}",
            event.getConnectionId(), event.getRequesterId(), event.getAddresseeId(), partition, offset);
        
        try {
            // Recipient is the addressee (person receiving request)
            Long recipientUserId = event.getAddresseeId();
            
            // Create notification
            notificationService.createNotification(
                recipientUserId,
                NotificationType.CONNECTION_REQUESTED,
                "New Connection Request",
                buildConnectionRequestedMessage(event),
                "/connections/requests/" + event.getConnectionId(),
                "CONNECTION",
                event.getConnectionId().toString()
            );
            
            log.info("Successfully processed CONNECTION_REQUESTED event for connectionId={}", 
                event.getConnectionId());
            
            // Acknowledge message (mark as processed)
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process CONNECTION_REQUESTED event: connectionId={}, error={}",
                event.getConnectionId(), e.getMessage(), e);
            
            // Still acknowledge to prevent infinite retry
            // Failed event logged for manual investigation
            ack.acknowledge();
        }
    }

    /**
     * Listen to connection-accepted events.
     * 
     * Triggered When:
     * User B accepts connection request from User A
     * 
     * Notification Created:
     * - Recipient: User A (requesterId - person who sent request)
     * - Type: CONNECTION_ACCEPTED
     * - Message: "User B accepted your connection request"
     * - Action: Link to User B's profile
     * 
     * @param event The connection accepted event
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param ack Manual acknowledgment
     */
    @KafkaListener(
        topics = "${app.kafka.topics.connection-accepted}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleConnectionAccepted(
        @Payload ConnectionAcceptedEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment ack
    ) {
        log.info("Received CONNECTION_ACCEPTED event: connectionId={}, requester={}, addressee={}, partition={}, offset={}",
            event.getConnectionId(), event.getRequesterId(), event.getAddresseeId(), partition, offset);
        
        try {
            // Recipient is the requester (person who sent original request)
            Long recipientUserId = event.getRequesterId();
            
            // Create notification
            notificationService.createNotification(
                recipientUserId,
                NotificationType.CONNECTION_ACCEPTED,
                "Connection Accepted!",
                buildConnectionAcceptedMessage(event),
                "/profile/" + event.getAddresseeId(),
                "CONNECTION",
                event.getConnectionId().toString()
            );
            
            log.info("Successfully processed CONNECTION_ACCEPTED event for connectionId={}", 
                event.getConnectionId());
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process CONNECTION_ACCEPTED event: connectionId={}, error={}",
                event.getConnectionId(), e.getMessage(), e);
            ack.acknowledge();
        }
    }

    /**
     * Listen to connection-rejected events.
     * 
     * Triggered When:
     * User B rejects connection request from User A
     * 
     * Notification Created:
     * - Recipient: User A (requesterId)
     * - Type: CONNECTION_REJECTED
     * - Message: "Your connection request was declined"
     * - Note: Generic message (don't expose who rejected)
     * 
     * @param event The connection rejected event
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param ack Manual acknowledgment
     */
    @KafkaListener(
        topics = "${app.kafka.topics.connection-rejected}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleConnectionRejected(
        @Payload ConnectionRejectedEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment ack
    ) {
        log.info("Received CONNECTION_REJECTED event: connectionId={}, requester={}, addressee={}, partition={}, offset={}",
            event.getConnectionId(), event.getRequesterId(), event.getAddresseeId(), partition, offset);
        
        try {
            // Recipient is the requester
            Long recipientUserId = event.getRequesterId();
            
            // Create notification (generic message for privacy)
            notificationService.createNotification(
                recipientUserId,
                NotificationType.CONNECTION_REJECTED,
                "Connection Request Declined",
                "Your connection request was not accepted.",
                "/connections/find-people",
                "CONNECTION",
                event.getConnectionId().toString()
            );
            
            log.info("Successfully processed CONNECTION_REJECTED event for connectionId={}", 
                event.getConnectionId());
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process CONNECTION_REJECTED event: connectionId={}, error={}",
                event.getConnectionId(), e.getMessage(), e);
            ack.acknowledge();
        }
    }

    /**
     * Listen to connection-blocked events.
     * 
     * Triggered When:
     * User B blocks User A
     * 
     * Notification Created:
     * - None (we don't notify blocked user for safety/privacy reasons)
     * - Just log the event
     * 
     * @param event The connection blocked event
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param ack Manual acknowledgment
     */
    @KafkaListener(
        topics = "${app.kafka.topics.connection-blocked}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleConnectionBlocked(
        @Payload ConnectionBlockedEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment ack
    ) {
        log.info("Received CONNECTION_BLOCKED event: connectionId={}, blocker={}, blocked={}, partition={}, offset={}",
            event.getConnectionId(), event.getBlockerId(), event.getBlockedId(), partition, offset);
        
        try {
            // Privacy/Safety: Don't notify blocked user
            // Just acknowledge and log
            log.info("CONNECTION_BLOCKED event processed (no notification sent for privacy)");
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process CONNECTION_BLOCKED event: connectionId={}, error={}",
                event.getConnectionId(), e.getMessage(), e);
            ack.acknowledge();
        }
    }

    /**
     * Listen to connection-cancelled events.
     * 
     * Triggered When:
     * User A cancels their own pending connection request to User B
     * 
     * Notification Created:
     * - None (user who cancelled knows they cancelled)
     * - Could optionally notify User B (addressee)
     * 
     * @param event The connection cancelled event
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param ack Manual acknowledgment
     */
    @KafkaListener(
        topics = "${app.kafka.topics.connection-cancelled}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleConnectionCancelled(
        @Payload ConnectionCancelledEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment ack
    ) {
        log.info("Received CONNECTION_CANCELLED event: connectionId={}, requester={}, addressee={}, partition={}, offset={}",
            event.getConnectionId(), event.getRequesterId(), event.getAddresseeId(), partition, offset);
        
        try {
            // Optional: Notify addressee that request was withdrawn
            // For now, just log
            log.info("CONNECTION_CANCELLED event processed (no notification sent)");
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process CONNECTION_CANCELLED event: connectionId={}, error={}",
                event.getConnectionId(), e.getMessage(), e);
            ack.acknowledge();
        }
    }

    /**
     * Listen to connection-removed events.
     * 
     * Triggered When:
     * User A or User B removes their established connection
     * 
     * Notification Created:
     * - None (removing connection is private action)
     * - Just log the event
     * 
     * @param event The connection removed event
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param ack Manual acknowledgment
     */
    @KafkaListener(
        topics = "${app.kafka.topics.connection-removed}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleConnectionRemoved(
        @Payload ConnectionRemovedEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment ack
    ) {
        log.info("Received CONNECTION_REMOVED event: connectionId={}, user1={}, user2={}, partition={}, offset={}",
            event.getConnectionId(), event.getUserId1(), event.getUserId2(), partition, offset);
        
        try {
            // Privacy: Don't notify when connection removed
            log.info("CONNECTION_REMOVED event processed (no notification sent for privacy)");
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process CONNECTION_REMOVED event: connectionId={}, error={}",
                event.getConnectionId(), e.getMessage(), e);
            ack.acknowledge();
        }
    }

    // =========================================================================
    // Helper Methods (Build Notification Messages)
    // =========================================================================

    /**
     * Build notification message for connection requested event.
     * 
     * @param event The event
     * @return Notification message
     */
    private String buildConnectionRequestedMessage(ConnectionRequestedEvent event) {
        try {
            com.linkedin.notification.user.dto.UserResponse requester = 
                userServiceClient.getUserById(event.getRequesterId());
            return requester.getName() + " wants to connect with you";
        } catch (Exception e) {
            log.warn("Failed to fetch requester details for userId {}: {}", 
                event.getRequesterId(), e.getMessage());
            return "You have a new connection request. Check your pending requests to respond.";
        }
    }

    /**
     * Build notification message for connection accepted event.
     * 
     * @param event The event
     * @return Notification message
     */
    private String buildConnectionAcceptedMessage(ConnectionAcceptedEvent event) {
        try {
            com.linkedin.notification.user.dto.UserResponse accepter = 
                userServiceClient.getUserById(event.getAddresseeId());
            return accepter.getName() + " accepted your connection request!";
        } catch (Exception e) {
            log.warn("Failed to fetch accepter details for userId {}: {}", 
                event.getAddresseeId(), e.getMessage());
            return "Your connection request was accepted! You can now see each other's updates.";
        }
    }
}

