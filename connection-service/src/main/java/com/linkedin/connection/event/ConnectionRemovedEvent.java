package com.linkedin.connection.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event published when a user removes an existing connection.
 * 
 * Published to Kafka topic: "connection-removed"
 * 
 * When Published:
 * - User A or User B removes their connection (unfriend)
 * - After connection is deleted from database
 * - Connection was in ACCEPTED state before removal
 * 
 * Who Consumes:
 * 1. Search Service: Update search index
 *    - User A can no longer see User B's posts
 *    - User B can no longer see User A's posts
 * 2. Analytics Service: Track removal metrics
 *    - Connection churn rate
 *    - Average connection duration
 * 3. Recommendation Service: Update suggestions
 *    - May suggest reconnection later
 * 
 * Privacy Note: The other user is NOT notified about removal.
 * They'll just notice the connection is gone.
 * 
 * Example Event:
 * <pre>
 * {
 *   "eventId": "aa0fd955-j7cg-96i9-f26b-99b1aaa95555",
 *   "eventType": "CONNECTION_REMOVED",
 *   "timestamp": "2025-12-21T16:30:00",
 *   "connectionId": 1,
 *   "requesterId": 123,
 *   "addresseeId": 456,
 *   "removedAt": "2025-12-21T16:30:00",
 *   "removedBy": 123
 * }
 * </pre>
 * 
 * @see com.linkedin.connection.service.ConnectionService#removeConnection
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ConnectionRemovedEvent extends BaseConnectionEvent {

    /**
     * Timestamp when the connection was removed.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime removedAt;

    /**
     * ID of the user who removed the connection.
     * Either requesterId or addresseeId (either can remove).
     */
    private Long removedBy;

    /**
     * Constructor that initializes event type and common fields.
     */
    public ConnectionRemovedEvent(Long connectionId, Long requesterId, Long addresseeId, 
                                 LocalDateTime removedAt, Long removedBy) {
        this.setConnectionId(connectionId);
        this.setRequesterId(requesterId);
        this.setAddresseeId(addresseeId);
        this.removedAt = removedAt;
        this.removedBy = removedBy;
        initializeCommonFields("CONNECTION_REMOVED");
    }
}

