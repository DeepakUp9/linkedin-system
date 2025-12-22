package com.linkedin.connection.patterns.state;

import com.linkedin.connection.model.Connection;
import com.linkedin.connection.model.ConnectionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * State Handler for BLOCKED connections.
 * 
 * A BLOCKED connection represents the strongest form of rejection.
 * The addressee has not only declined the request but has also blocked
 * the requester, preventing any future connection requests.
 * 
 * Allowed Transitions:
 * - None (terminal state, requires manual unblock to reverse)
 * 
 * Allowed Actions:
 * - None (blocked state is permanent until admin intervention)
 * 
 * Not Allowed:
 * - accept() - Cannot accept a block
 * - reject() - Already blocked (stronger than reject)
 * - block() - Already blocked
 * - remove() - Terminal state, no removal
 * - cancel() - Cannot cancel (only PENDING can be cancelled)
 * 
 * State Characteristics:
 * - Terminal state (strictest, no transitions)
 * - Prevents future connection requests from requester to addressee
 * - Kept permanently for security and spam prevention
 * - May require admin intervention or manual unblock to reverse
 * 
 * Business Rules:
 * - Blocked users cannot:
 *   • Send connection requests to the blocker
 *   • View the blocker's full profile
 *   • Message the blocker
 * - Blocked state is one-directional (A blocks B ≠ B blocks A)
 * - Blocker can manually unblock (not implemented via state transitions)
 * 
 * Difference from REJECTED:
 * - REJECTED: "Not interested right now, maybe later"
 * - BLOCKED: "Never want to hear from this person"
 * 
 * @see ConnectionStateHandler
 * @see Connection
 * @see ConnectionState
 */
@Component
@Slf4j
public class BlockedStateHandler implements ConnectionStateHandler {

    @Override
    public void accept(Connection connection) {
        log.error("Cannot accept BLOCKED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Connection is blocked. Cannot accept a blocked connection request."
        );
    }

    @Override
    public void reject(Connection connection) {
        log.error("Cannot reject BLOCKED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Connection is already blocked. Blocking is a stronger action than rejecting."
        );
    }

    @Override
    public void block(Connection connection) {
        log.error("Cannot block BLOCKED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Connection is already in BLOCKED state."
        );
    }

    @Override
    public void remove(Connection connection) {
        log.error("Cannot remove BLOCKED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Cannot remove a BLOCKED connection. " +
            "Blocked connections are kept permanently for security. " +
            "To allow future connections, manually unblock the user."
        );
    }

    @Override
    public void cancel(Connection connection) {
        log.error("Cannot cancel BLOCKED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Cannot cancel a blocked connection. The requester has been blocked."
        );
    }

    @Override
    public boolean canAccept(Connection connection) {
        return false;
    }

    @Override
    public boolean canReject(Connection connection) {
        return false;
    }

    @Override
    public boolean canBlock(Connection connection) {
        return false;
    }

    @Override
    public boolean canRemove(Connection connection) {
        return false;
    }

    @Override
    public boolean canCancel(Connection connection) {
        return false;
    }

    @Override
    public ConnectionState getHandledState() {
        return ConnectionState.BLOCKED;
    }

    @Override
    public boolean isTerminalState() {
        return true; // Strictest terminal state
    }

    @Override
    public String getAllowedActionsDescription() {
        return "No actions allowed. This is a terminal state. " +
               "The requester is blocked and cannot send future requests. " +
               "Contact support to unblock.";
    }
}

