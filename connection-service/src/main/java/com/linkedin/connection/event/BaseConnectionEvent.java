package com.linkedin.connection.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all connection-related events published to Kafka.
 * 
 * Purpose:
 * Provides common fields for all events (eventId, timestamp, eventType).
 * All specific event classes inherit from this base class.
 * 
 * Event-Driven Architecture:
 * When something important happens in Connection Service, we publish an event to Kafka.
 * Other services (Notification, Analytics, Search) subscribe to these events and react.
 * 
 * Why Events?
 * 1. Decoupling: Connection Service doesn't need to know about other services
 * 2. Async: Connection Service responds immediately, other services process later
 * 3. Reliability: If a service is down, event waits in Kafka until it's back up
 * 4. Scalability: Easy to add new services that react to events
 * 5. Audit Trail: All events are stored in Kafka for a retention period
 * 
 * Event Flow Example:
 * <pre>
 * User accepts connection request
 *   ↓
 * Connection Service: connection.accept()
 *   ↓
 * ConnectionService: Publish ConnectionAcceptedEvent
 *   ↓
 * Kafka: Store event in "connection-accepted" topic
 *   ↓
 * Notification Service: Read event → Send email to requester
 *   ↓
 * Analytics Service: Read event → Update connection stats
 *   ↓
 * Search Service: Read event → Update search index
 * </pre>
 * 
 * JSON Representation (sent to Kafka):
 * <pre>
 * {
 *   "eventId": "550e8400-e29b-41d4-a716-446655440000",
 *   "eventType": "CONNECTION_ACCEPTED",
 *   "timestamp": "2025-12-21T12:00:00",
 *   "connectionId": 1,
 *   "requesterId": 123,
 *   "addresseeId": 456
 * }
 * </pre>
 * 
 * @see ConnectionRequestedEvent
 * @see ConnectionAcceptedEvent
 * @see ConnectionRejectedEvent
 * 
 * @author LinkedIn System
 * @version 1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseConnectionEvent {

    /**
     * Unique identifier for this event.
     * Generated using UUID to ensure uniqueness across all events.
     * 
     * Use Cases:
     * - Idempotency: Prevent processing the same event twice
     * - Tracing: Track event flow across services
     * - Debugging: Identify specific events in logs
     */
    private String eventId;

    /**
     * Type of event (e.g., "CONNECTION_ACCEPTED", "CONNECTION_REQUESTED").
     * Used by consumers to determine how to process the event.
     */
    private String eventType;

    /**
     * Timestamp when the event was created.
     * ISO 8601 format: yyyy-MM-dd'T'HH:mm:ss
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * ID of the connection entity this event relates to.
     */
    private Long connectionId;

    /**
     * ID of the user who initiated the connection request.
     */
    private Long requesterId;

    /**
     * ID of the user who received the connection request.
     */
    private Long addresseeId;

    /**
     * Generates a new unique event ID.
     * Called before publishing event to Kafka.
     * 
     * @return UUID string
     */
    public static String generateEventId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Sets common fields for the event.
     * Should be called in constructor of subclasses.
     */
    protected void initializeCommonFields(String eventType) {
        if (this.eventId == null) {
            this.eventId = generateEventId();
        }
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
        this.eventType = eventType;
    }
}

