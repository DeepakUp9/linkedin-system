package com.linkedin.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event representing a removed connection.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConnectionRemovedEvent extends BaseConnectionEvent {
    private Long connectionId;
    private Long userId1;
    private Long userId2;
    private LocalDateTime removedAt;
}

