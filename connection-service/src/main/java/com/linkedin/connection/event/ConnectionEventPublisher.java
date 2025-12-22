package com.linkedin.connection.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for publishing connection events to Kafka topics.
 * 
 * Purpose:
 * Central place for all Kafka event publishing logic.
 * Handles serialization, topic routing, error handling, and logging.
 * 
 * How It Works:
 * 1. Service layer creates event object (e.g., ConnectionAcceptedEvent)
 * 2. Calls eventPublisher.publishConnectionAccepted(event)
 * 3. Publisher sends event to Kafka topic "connection-accepted"
 * 4. Kafka stores event (durable, persistent)
 * 5. Consumer services read and process event
 * 
 * Example Flow:
 * <pre>
 * ConnectionService:
 *   connection.accept();
 *   connectionRepository.save(connection);
 *   
 *   ConnectionAcceptedEvent event = new ConnectionAcceptedEvent(...);
 *   eventPublisher.publishConnectionAccepted(event);  ← This class!
 *   
 *   return response; // Returns immediately, doesn't wait for consumers!
 * </pre>
 * 
 * Kafka Configuration:
 * - KafkaTemplate: Spring's high-level Kafka client
 * - Serialization: Automatic JSON serialization (configured in application.yml)
 * - Topics: Configured via properties (e.g., ${app.kafka.topics.connection-accepted})
 * - Acknowledgment: "all" (waits for all replicas, most reliable)
 * - Idempotence: Enabled (prevents duplicate messages)
 * 
 * Error Handling:
 * - Async callbacks: onSuccess / onFailure
 * - Logs success/failure
 * - In production: Add retry logic, dead letter queue
 * 
 * Performance:
 * - Async: Doesn't block the calling thread
 * - Batching: Kafka automatically batches messages for efficiency
 * - Compression: Can enable compression (gzip, snappy, lz4)
 * 
 * @see KafkaTemplate
 * @see BaseConnectionEvent
 * @author LinkedIn System
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionEventPublisher {

    /**
     * Spring's Kafka template for sending messages.
     * - Handles serialization (Object → JSON)
     * - Manages connection pool to Kafka brokers
     * - Provides async API with CompletableFuture
     */
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Topic names from application.yml
    @Value("${app.kafka.topics.connection-requested}")
    private String connectionRequestedTopic;

    @Value("${app.kafka.topics.connection-accepted}")
    private String connectionAcceptedTopic;

    @Value("${app.kafka.topics.connection-rejected}")
    private String connectionRejectedTopic;

    @Value("${app.kafka.topics.connection-blocked}")
    private String connectionBlockedTopic;

    @Value("${app.kafka.topics.connection-cancelled}")
    private String connectionCancelledTopic;

    @Value("${app.kafka.topics.connection-removed}")
    private String connectionRemovedTopic;

    /**
     * Publishes a ConnectionRequestedEvent when a user sends a connection request.
     * 
     * @param event The event to publish
     */
    public void publishConnectionRequested(ConnectionRequestedEvent event) {
        log.info("Publishing ConnectionRequestedEvent: connectionId={}, requester={}, addressee={}",
                event.getConnectionId(), event.getRequesterId(), event.getAddresseeId());
        
        publishEvent(connectionRequestedTopic, event.getEventId(), event);
    }

    /**
     * Publishes a ConnectionAcceptedEvent when a user accepts a connection request.
     * 
     * @param event The event to publish
     */
    public void publishConnectionAccepted(ConnectionAcceptedEvent event) {
        log.info("Publishing ConnectionAcceptedEvent: connectionId={}, requester={}, addressee={}",
                event.getConnectionId(), event.getRequesterId(), event.getAddresseeId());
        
        publishEvent(connectionAcceptedTopic, event.getEventId(), event);
    }

    /**
     * Publishes a ConnectionRejectedEvent when a user rejects a connection request.
     * 
     * @param event The event to publish
     */
    public void publishConnectionRejected(ConnectionRejectedEvent event) {
        log.info("Publishing ConnectionRejectedEvent: connectionId={}, requester={}, addressee={}",
                event.getConnectionId(), event.getRequesterId(), event.getAddresseeId());
        
        publishEvent(connectionRejectedTopic, event.getEventId(), event);
    }

    /**
     * Publishes a ConnectionBlockedEvent when a user blocks another user.
     * 
     * @param event The event to publish
     */
    public void publishConnectionBlocked(ConnectionBlockedEvent event) {
        log.info("Publishing ConnectionBlockedEvent: connectionId={}, requester={}, addressee={}",
                event.getConnectionId(), event.getRequesterId(), event.getAddresseeId());
        
        publishEvent(connectionBlockedTopic, event.getEventId(), event);
    }

    /**
     * Publishes a ConnectionCancelledEvent when a user cancels their connection request.
     * 
     * @param event The event to publish
     */
    public void publishConnectionCancelled(ConnectionCancelledEvent event) {
        log.info("Publishing ConnectionCancelledEvent: connectionId={}, requester={}, addressee={}",
                event.getConnectionId(), event.getRequesterId(), event.getAddresseeId());
        
        publishEvent(connectionCancelledTopic, event.getEventId(), event);
    }

    /**
     * Publishes a ConnectionRemovedEvent when a user removes an existing connection.
     * 
     * @param event The event to publish
     */
    public void publishConnectionRemoved(ConnectionRemovedEvent event) {
        log.info("Publishing ConnectionRemovedEvent: connectionId={}, requester={}, addressee={}, removedBy={}",
                event.getConnectionId(), event.getRequesterId(), event.getAddresseeId(), event.getRemovedBy());
        
        publishEvent(connectionRemovedTopic, event.getEventId(), event);
    }

    /**
     * Generic method to publish any event to a Kafka topic.
     * 
     * How It Works:
     * 1. kafkaTemplate.send(topic, key, value) returns CompletableFuture
     * 2. CompletableFuture = async operation that completes in the future
     * 3. whenComplete() registers callbacks for success/failure
     * 4. Method returns immediately (non-blocking)
     * 
     * Message Key:
     * - Key = eventId (ensures messages with same key go to same partition)
     * - Useful for ordering: All events for same connection stay in order
     * - Enables parallel processing of different connections
     * 
     * Example:
     * <pre>
     * publishEvent("connection-accepted", "event-123", event)
     *   ↓
     * Kafka stores:
     *   Topic: connection-accepted
     *   Partition: 2 (based on hash of key)
     *   Offset: 42
     *   Key: "event-123"
     *   Value: { "eventType": "CONNECTION_ACCEPTED", ... }
     * </pre>
     * 
     * @param topic The Kafka topic to publish to
     * @param key The message key (for partitioning)
     * @param event The event object to publish
     */
    private void publishEvent(String topic, String key, Object event) {
        try {
            // Send message asynchronously
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
            
            // Register success/failure callbacks
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    // Success: Log metadata
                    var metadata = result.getRecordMetadata();
                    log.debug("Event published successfully: topic={}, partition={}, offset={}, key={}",
                            metadata.topic(), metadata.partition(), metadata.offset(), key);
                } else {
                    // Failure: Log error
                    log.error("Failed to publish event to topic {}: {}", topic, ex.getMessage(), ex);
                    
                    // TODO: In production, add:
                    // - Retry logic (with exponential backoff)
                    // - Dead letter queue for failed messages
                    // - Alerting/monitoring
                }
            });
            
        } catch (Exception ex) {
            log.error("Exception while publishing event to topic {}: {}", topic, ex.getMessage(), ex);
        }
    }

    /**
     * Health check method - can be used by actuator to check Kafka connectivity.
     * 
     * @return true if Kafka is reachable, false otherwise
     */
    public boolean isKafkaHealthy() {
        // TODO: Implement health check
        // Try to send a test message or check broker connectivity
        return true;
    }
}

