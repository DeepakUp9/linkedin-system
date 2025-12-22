package com.linkedin.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event representing an accepted connection request.
 * 
 * Triggered When:
 * - User B accepts connection request from User A
 * 
 * Published By:
 * - connection-service
 * 
 * Consumed By:
 * - notification-service (this service)
 * 
 * Notification Created:
 * - To: User A (requesterId)
 * - Type: CONNECTION_ACCEPTED
 * - Message: "User B accepted your connection request"
 * - Channels: Email + In-App (based on preferences)
 * 
 * Example Payload:
 * {
 *   "eventType": "CONNECTION_ACCEPTED",
 *   "eventId": "550e8400-e29b-41d4-a716-446655440001",
 *   "timestamp": "2024-01-15T10:35:00",
 *   "connectionId": 123,
 *   "requesterId": 456,
 *   "addresseeId": 789,
 *   "acceptedAt": "2024-01-15T10:35:00"
 * }
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConnectionAcceptedEvent extends BaseConnectionEvent {

    private Long connectionId;
    private Long requesterId;
    private Long addresseeId;
    private LocalDateTime acceptedAt;
}

