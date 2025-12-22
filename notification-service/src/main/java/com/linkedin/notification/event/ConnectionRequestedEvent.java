package com.linkedin.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event representing a new connection request.
 * 
 * Triggered When:
 * - User A sends connection request to User B
 * 
 * Published By:
 * - connection-service
 * 
 * Consumed By:
 * - notification-service (this service)
 * 
 * Notification Created:
 * - To: User B (addresseeId)
 * - Type: CONNECTION_REQUESTED
 * - Message: "User A sent you a connection request"
 * - Channels: Email + In-App (based on preferences)
 * 
 * Example Payload:
 * {
 *   "eventType": "CONNECTION_REQUESTED",
 *   "eventId": "550e8400-e29b-41d4-a716-446655440000",
 *   "timestamp": "2024-01-15T10:30:00",
 *   "connectionId": 123,
 *   "requesterId": 456,
 *   "addresseeId": 789,
 *   "message": "I'd like to connect with you",
 *   "requestedAt": "2024-01-15T10:30:00"
 * }
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConnectionRequestedEvent extends BaseConnectionEvent {

    /**
     * ID of the connection request
     */
    private Long connectionId;

    /**
     * ID of the user who sent the request
     */
    private Long requesterId;

    /**
     * ID of the user who received the request
     */
    private Long addresseeId;

    /**
     * Optional message from requester
     */
    private String message;

    /**
     * When the request was made
     */
    private LocalDateTime requestedAt;
}

