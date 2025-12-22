package com.linkedin.connection.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event published when a user rejects a connection request.
 * 
 * Published to Kafka topic: "connection-rejected"
 * 
 * When Published:
 * - User B rejects connection request from User A
 * - After connection state changes: PENDING → REJECTED
 * 
 * Who Consumes:
 * 1. Analytics Service: Track rejection metrics
 *    - Rejection rate
 *    - Reasons for rejection (if provided in future)
 * 2. Recommendation Service: Update suggestions
 *    - Don't suggest User B to User A again (for some time)
 * 
 * Note: We typically DON'T notify the requester about rejection
 * for privacy reasons (they just see request as "Pending" forever).
 * 
 * Example Event:
 * <pre>
 * {
 *   "eventId": "770fa622-g49d-63f6-c938-668877662222",
 *   "eventType": "CONNECTION_REJECTED",
 *   "timestamp": "2025-12-21T15:00:00",
 *   "connectionId": 1,
 *   "requesterId": 123,
 *   "addresseeId": 456,
 *   "rejectedAt": "2025-12-21T15:00:00"
 * }
 * </pre>
 * 
 * @see com.linkedin.connection.service.ConnectionService#rejectConnectionRequest
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ConnectionRejectedEvent extends BaseConnectionEvent {

    /**
     * Timestamp when the request was rejected.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime rejectedAt;

    /**
     * Constructor that initializes event type and common fields.
     */
    public ConnectionRejectedEvent(Long connectionId, Long requesterId, Long addresseeId, 
                                  LocalDateTime rejectedAt) {
        this.setConnectionId(connectionId);
        this.setRequesterId(requesterId);
        this.setAddresseeId(addresseeId);
        this.rejectedAt = rejectedAt;
        initializeCommonFields("CONNECTION_REJECTED");
    }
}

