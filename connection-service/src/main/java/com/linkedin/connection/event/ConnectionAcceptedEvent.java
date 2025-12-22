package com.linkedin.connection.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event published when a user accepts a connection request.
 * 
 * Published to Kafka topic: "connection-accepted"
 * 
 * When Published:
 * - User B accepts connection request from User A
 * - After connection state changes: PENDING → ACCEPTED
 * 
 * Who Consumes:
 * 1. Notification Service: Send notification to User A (requester)
 *    - Email: "Jane Doe accepted your connection request!"
 *    - Push: "Jane is now in your network"
 * 2. Analytics Service: Track connection metrics
 *    - Total connections count
 *    - Acceptance rate
 *    - Network growth statistics
 * 3. Search Service: Update search index
 *    - User A can now see User B's posts/content
 *    - User B can now see User A's posts/content
 * 4. Recommendation Service: Update suggestions
 *    - Update "People You May Know" for mutual connections
 * 
 * Example Event:
 * <pre>
 * {
 *   "eventId": "660f9511-f39c-52e5-b827-557766551111",
 *   "eventType": "CONNECTION_ACCEPTED",
 *   "timestamp": "2025-12-21T14:30:00",
 *   "connectionId": 1,
 *   "requesterId": 123,
 *   "addresseeId": 456,
 *   "acceptedAt": "2025-12-21T14:30:00"
 * }
 * </pre>
 * 
 * @see com.linkedin.connection.service.ConnectionService#acceptConnectionRequest
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ConnectionAcceptedEvent extends BaseConnectionEvent {

    /**
     * Timestamp when the request was accepted.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime acceptedAt;

    /**
     * Constructor that initializes event type and common fields.
     */
    public ConnectionAcceptedEvent(Long connectionId, Long requesterId, Long addresseeId, 
                                  LocalDateTime acceptedAt) {
        this.setConnectionId(connectionId);
        this.setRequesterId(requesterId);
        this.setAddresseeId(addresseeId);
        this.acceptedAt = acceptedAt;
        initializeCommonFields("CONNECTION_ACCEPTED");
    }
}

