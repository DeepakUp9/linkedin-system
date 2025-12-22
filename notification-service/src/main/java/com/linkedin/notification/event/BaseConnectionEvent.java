package com.linkedin.notification.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Base class for all connection-related events consumed from Kafka.
 * 
 * Purpose:
 * - Mirrors the event structure published by connection-service
 * - Enables polymorphic deserialization (different event types from same topic)
 * 
 * How It Works:
 * <pre>
 * Connection Service publishes:
 * {
 *   "eventType": "CONNECTION_ACCEPTED",
 *   "connectionId": 123,
 *   "requesterId": 456,
 *   "addresseeId": 789,
 *   ...
 * }
 * 
 * Kafka delivers event to Notification Service
 * 
 * Jackson deserializes:
 * - Looks at "eventType" field
 * - Maps to ConnectionAcceptedEvent.class
 * - Creates correct object type
 * </pre>
 * 
 * Design Pattern: Polymorphic Events
 * - @JsonTypeInfo: Tells Jackson to use "eventType" for type discrimination
 * - @JsonSubTypes: Maps eventType values to concrete classes
 * 
 * @see ConnectionAcceptedEvent
 * @see ConnectionRequestedEvent
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "eventType"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ConnectionRequestedEvent.class, name = "CONNECTION_REQUESTED"),
    @JsonSubTypes.Type(value = ConnectionAcceptedEvent.class, name = "CONNECTION_ACCEPTED"),
    @JsonSubTypes.Type(value = ConnectionRejectedEvent.class, name = "CONNECTION_REJECTED"),
    @JsonSubTypes.Type(value = ConnectionBlockedEvent.class, name = "CONNECTION_BLOCKED"),
    @JsonSubTypes.Type(value = ConnectionCancelledEvent.class, name = "CONNECTION_CANCELLED"),
    @JsonSubTypes.Type(value = ConnectionRemovedEvent.class, name = "CONNECTION_REMOVED")
})
public abstract class BaseConnectionEvent {

    /**
     * Unique identifier for the event (UUID)
     */
    private String eventId;

    /**
     * When the event occurred
     */
    private LocalDateTime timestamp;

    /**
     * Type of the event (e.g., "CONNECTION_ACCEPTED")
     * Used by Jackson for deserialization
     */
    private String eventType;
}

