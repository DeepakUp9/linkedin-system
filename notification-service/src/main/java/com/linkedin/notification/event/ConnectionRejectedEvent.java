package com.linkedin.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event representing a rejected connection request.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConnectionRejectedEvent extends BaseConnectionEvent {
    private Long connectionId;
    private Long requesterId;
    private Long addresseeId;
    private LocalDateTime rejectedAt;
}

