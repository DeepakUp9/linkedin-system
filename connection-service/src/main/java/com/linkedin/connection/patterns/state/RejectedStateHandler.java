package com.linkedin.connection.patterns.state;

import com.linkedin.connection.model.Connection;
import com.linkedin.connection.model.ConnectionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * State Handler for REJECTED connections.
 * 
 * A REJECTED connection represents a connection request that was declined
 * by the addressee. This is a "soft rejection" - the requester can send
 * a new request in the future (after a cooldown period).
 * 
 * Allowed Transitions:
 * - None (terminal state)
 * 
 * Allowed Actions:
 * - None (records kept for analytics, eventually cleaned up)
 * 
 * Not Allowed:
 * - accept() - Can't accept after rejection
 * - reject() - Already rejected
 * - block() - Can't block (already rejected)
 * - remove() - Terminal state, no removal
 * - cancel() - Can't cancel (only PENDING can be cancelled)
 * 
 * State Characteristics:
 * - Terminal state (no transitions)
 * - Kept for analytics and spam prevention
 * - Eventually cleaned up by scheduled job (e.g., after 30 days)
 * - Doesn't prevent future connection requests (unlike BLOCKED)
 * 
 * Business Rules:
 * - Rejected records help prevent spam (rate limiting)
 * - After rejection, requester may be notified once
 * - Requester can try again after cooldown period
 * 
 * @see ConnectionStateHandler
 * @see Connection
 * @see ConnectionState
 */
@Component
@Slf4j
public class RejectedStateHandler implements ConnectionStateHandler {

    @Override
    public void accept(Connection connection) {
        log.error("Cannot accept REJECTED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Connection has been rejected. Cannot accept a rejected connection. " +
            "To reconnect, send a new connection request."
        );
    }

    @Override
    public void reject(Connection connection) {
        log.error("Cannot reject REJECTED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Connection is already in REJECTED state."
        );
    }

    @Override
    public void block(Connection connection) {
        log.error("Cannot block REJECTED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Cannot block a rejected connection. The connection is already declined. " +
            "If you want to prevent future requests, block the user separately."
        );
    }

    @Override
    public void remove(Connection connection) {
        log.error("Cannot remove REJECTED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Cannot remove a REJECTED connection. " +
            "Rejected connections are kept for analytics and will be automatically cleaned up."
        );
    }

    @Override
    public void cancel(Connection connection) {
        log.error("Cannot cancel REJECTED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Cannot cancel a rejected connection. The request has already been declined."
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
        return ConnectionState.REJECTED;
    }

    @Override
    public boolean isTerminalState() {
        return true; // No further actions allowed
    }

    @Override
    public String getAllowedActionsDescription() {
        return "No actions allowed. This is a terminal state. " +
               "To reconnect, send a new connection request.";
    }
}

