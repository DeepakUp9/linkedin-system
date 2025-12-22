package com.linkedin.connection.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event published when a user blocks another user.
 * 
 * Published to Kafka topic: "connection-blocked"
 * 
 * When Published:
 * - User B blocks User A (usually from a pending connection request)
 * - After connection state changes: PENDING → BLOCKED
 * 
 * Who Consumes:
 * 1. User Service: Update block list
 *    - Prevent User A from sending future requests to User B
 *    - Hide User B from User A's search results
 * 2. Analytics Service: Track blocking metrics
 *    - Number of blocks per day
 *    - Users who frequently get blocked (spam detection)
 * 3. Moderation Service: Flag for review
 *    - If a user gets blocked too often, may need moderation
 * 
 * Privacy Note: The requester (User A) is NOT notified about being blocked.
 * 
 * Example Event:
 * <pre>
 * {
 *   "eventId": "880fb733-h5ae-74g7-d049-779988773333",
 *   "eventType": "CONNECTION_BLOCKED",
 *   "timestamp": "2025-12-21T15:30:00",
 *   "connectionId": 1,
 *   "requesterId": 123,
 *   "addresseeId": 456,
 *   "blockedAt": "2025-12-21T15:30:00"
 * }
 * </pre>
 * 
 * @see com.linkedin.connection.service.ConnectionService#blockUser
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ConnectionBlockedEvent extends BaseConnectionEvent {

    /**
     * Timestamp when the user was blocked.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime blockedAt;

    /**
     * Constructor that initializes event type and common fields.
     */
    public ConnectionBlockedEvent(Long connectionId, Long requesterId, Long addresseeId, 
                                 LocalDateTime blockedAt) {
        this.setConnectionId(connectionId);
        this.setRequesterId(requesterId);
        this.setAddresseeId(addresseeId);
        this.blockedAt = blockedAt;
        initializeCommonFields("CONNECTION_BLOCKED");
    }
}

