package com.linkedin.connection.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event published when a user cancels their own connection request.
 * 
 * Published to Kafka topic: "connection-cancelled"
 * 
 * When Published:
 * - User A cancels their pending connection request to User B
 * - Before the connection entity is deleted from database
 * 
 * Who Consumes:
 * 1. Notification Service: Remove pending notification (if not sent yet)
 *    - Don't send "You have a connection request" email if already queued
 * 2. Analytics Service: Track cancellation metrics
 *    - How often users cancel requests
 *    - Time between request and cancellation
 * 
 * Note: This is different from rejection
 * - Cancellation: Requester changes their mind ("I don't want to connect anymore")
 * - Rejection: Addressee declines ("I don't want to connect with you")
 * 
 * Example Event:
 * <pre>
 * {
 *   "eventId": "990fc844-i6bf-85h8-e15a-88a099884444",
 *   "eventType": "CONNECTION_CANCELLED",
 *   "timestamp": "2025-12-21T16:00:00",
 *   "connectionId": 1,
 *   "requesterId": 123,
 *   "addresseeId": 456,
 *   "cancelledAt": "2025-12-21T16:00:00"
 * }
 * </pre>
 * 
 * @see com.linkedin.connection.service.ConnectionService#cancelConnectionRequest
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ConnectionCancelledEvent extends BaseConnectionEvent {

    /**
     * Timestamp when the request was cancelled.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime cancelledAt;

    /**
     * Constructor that initializes event type and common fields.
     */
    public ConnectionCancelledEvent(Long connectionId, Long requesterId, Long addresseeId, 
                                   LocalDateTime cancelledAt) {
        this.setConnectionId(connectionId);
        this.setRequesterId(requesterId);
        this.setAddresseeId(addresseeId);
        this.cancelledAt = cancelledAt;
        initializeCommonFields("CONNECTION_CANCELLED");
    }
}

