package com.linkedin.connection.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event published when a user sends a connection request.
 * 
 * Published to Kafka topic: "connection-requested"
 * 
 * When Published:
 * - User A sends connection request to User B
 * - After connection is saved to database with state=PENDING
 * 
 * Who Consumes:
 * 1. Notification Service: Send email/push notification to User B
 *    - "John Doe wants to connect with you"
 * 2. Analytics Service: Track connection request metrics
 *    - Daily connection requests count
 *    - Popular users (who receives most requests)
 * 3. Recommendation Service: Update user graph
 *    - "People you may know" suggestions
 * 
 * Example Event:
 * <pre>
 * {
 *   "eventId": "550e8400-e29b-41d4-a716-446655440000",
 *   "eventType": "CONNECTION_REQUESTED",
 *   "timestamp": "2025-12-21T12:00:00",
 *   "connectionId": 1,
 *   "requesterId": 123,
 *   "addresseeId": 456,
 *   "message": "Hi Jane, let's connect!",
 *   "requestedAt": "2025-12-21T12:00:00"
 * }
 * </pre>
 * 
 * @see com.linkedin.connection.service.ConnectionService#sendConnectionRequest
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ConnectionRequestedEvent extends BaseConnectionEvent {

    /**
     * Optional message sent with the connection request.
     */
    private String message;

    /**
     * Timestamp when the request was sent.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime requestedAt;

    /**
     * Constructor that initializes event type and common fields.
     */
    public ConnectionRequestedEvent(Long connectionId, Long requesterId, Long addresseeId, 
                                   String message, LocalDateTime requestedAt) {
        this.setConnectionId(connectionId);
        this.setRequesterId(requesterId);
        this.setAddresseeId(addresseeId);
        this.message = message;
        this.requestedAt = requestedAt;
        initializeCommonFields("CONNECTION_REQUESTED");
    }
}

