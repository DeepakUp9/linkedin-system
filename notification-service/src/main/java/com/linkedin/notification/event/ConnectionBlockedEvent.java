package com.linkedin.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Event representing a blocked user.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ConnectionBlockedEvent extends BaseConnectionEvent {
    private Long connectionId;
    private Long blockerId;
    private Long blockedId;
    private LocalDateTime blockedAt;
}

