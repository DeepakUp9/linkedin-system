package com.linkedin.connection.patterns.state;

import com.linkedin.connection.model.Connection;
import com.linkedin.connection.model.ConnectionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * State Handler for PENDING connections.
 * 
 * A PENDING connection represents a connection request that has been sent
 * but not yet responded to by the addressee.
 * 
 * Allowed Transitions:
 * - PENDING → ACCEPTED (when addressee accepts)
 * - PENDING → REJECTED (when addressee rejects)
 * - PENDING → BLOCKED (when addressee blocks)
 * - PENDING → deleted (when requester cancels)
 * 
 * Allowed Actions:
 * - accept() - Addressee accepts the request
 * - reject() - Addressee rejects the request
 * - block() - Addressee blocks the requester
 * - cancel() - Requester cancels their request
 * 
 * Not Allowed:
 * - remove() - Can't remove PENDING (only ACCEPTED can be removed)
 * 
 * State Characteristics:
 * - Not terminal (has possible transitions)
 * - Requires action from addressee
 * - Can be cancelled by requester
 * 
 * @see ConnectionStateHandler
 * @see Connection
 * @see ConnectionState
 */
@Component
@Slf4j
public class PendingStateHandler implements ConnectionStateHandler {

    @Override
    public void accept(Connection connection) {
        log.info("Accepting connection request: ID={}, Requester={}, Addressee={}", 
            connection.getId(), connection.getRequesterId(), connection.getAddresseeId());
        
        // Transition to ACCEPTED state
        connection.accept();
        
        log.debug("Connection {} transitioned to ACCEPTED state", connection.getId());
        
        // Note: Event publishing is handled by ConnectionService layer
    }

    @Override
    public void reject(Connection connection) {
        log.info("Rejecting connection request: ID={}, Requester={}, Addressee={}", 
            connection.getId(), connection.getRequesterId(), connection.getAddresseeId());
        
        // Transition to REJECTED state
        connection.reject();
        
        log.debug("Connection {} transitioned to REJECTED state", connection.getId());
        
        // Note: Event publishing is handled by ConnectionService layer
    }

    @Override
    public void block(Connection connection) {
        log.info("Blocking requester: ID={}, Requester={}, Addressee={}", 
            connection.getId(), connection.getRequesterId(), connection.getAddresseeId());
        
        // Transition to BLOCKED state
        connection.block();
        
        log.warn("User {} blocked by user {}", 
            connection.getRequesterId(), connection.getAddresseeId());
        
        // Note: Event publishing is handled by ConnectionService layer
    }

    @Override
    public void remove(Connection connection) {
        log.error("Cannot remove PENDING connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Cannot remove a PENDING connection. Either accept/reject it, or cancel it."
        );
    }

    @Override
    public void cancel(Connection connection) {
        log.info("Cancelling connection request: ID={}, Requester={}, Addressee={}", 
            connection.getId(), connection.getRequesterId(), connection.getAddresseeId());
        
        // Cancellation is handled by deleting the record in the service layer
        // This method just validates that cancellation is allowed
        
        log.debug("Connection {} can be cancelled (will be deleted)", connection.getId());
        
        // Note: Event publishing is handled by ConnectionService layer
    }

    @Override
    public boolean canAccept(Connection connection) {
        return true; // PENDING connections can be accepted
    }

    @Override
    public boolean canReject(Connection connection) {
        return true; // PENDING connections can be rejected
    }

    @Override
    public boolean canBlock(Connection connection) {
        return true; // PENDING connections can result in blocking
    }

    @Override
    public boolean canRemove(Connection connection) {
        return false; // Can't remove PENDING (only ACCEPTED)
    }

    @Override
    public boolean canCancel(Connection connection) {
        return true; // PENDING connections can be cancelled
    }

    @Override
    public ConnectionState getHandledState() {
        return ConnectionState.PENDING;
    }

    @Override
    public boolean isTerminalState() {
        return false; // PENDING is not terminal, has transitions
    }

    @Override
    public String getAllowedActionsDescription() {
        return "Allowed actions: accept (by addressee), reject (by addressee), " +
               "block (by addressee), cancel (by requester)";
    }
}

