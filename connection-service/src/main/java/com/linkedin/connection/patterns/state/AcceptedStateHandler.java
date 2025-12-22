package com.linkedin.connection.patterns.state;

import com.linkedin.connection.model.Connection;
import com.linkedin.connection.model.ConnectionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * State Handler for ACCEPTED connections.
 * 
 * An ACCEPTED connection represents an active professional connection
 * between two users. Both users are now "connected" and can:
 * - See each other's full profiles
 * - Message each other
 * - See each other's posts and updates
 * - Appear in each other's "Connections" list
 * 
 * Allowed Transitions:
 * - ACCEPTED → deleted (when either user removes the connection)
 * 
 * Allowed Actions:
 * - remove() - Either user can remove/disconnect
 * 
 * Not Allowed:
 * - accept() - Already accepted
 * - reject() - Can't reject an accepted connection
 * - block() - To block, must first remove then block separately
 * - cancel() - Can't cancel (only requester of PENDING can cancel)
 * 
 * State Characteristics:
 * - Terminal in terms of state machine (no state transitions)
 * - But can be removed (deleted from database)
 * - Represents the successful outcome of a connection request
 * 
 * @see ConnectionStateHandler
 * @see Connection
 * @see ConnectionState
 */
@Component
@Slf4j
public class AcceptedStateHandler implements ConnectionStateHandler {

    @Override
    public void accept(Connection connection) {
        log.error("Cannot accept ACCEPTED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Connection is already accepted. Cannot accept again."
        );
    }

    @Override
    public void reject(Connection connection) {
        log.error("Cannot reject ACCEPTED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Cannot reject an accepted connection. To disconnect, use remove() instead."
        );
    }

    @Override
    public void block(Connection connection) {
        log.error("Cannot block ACCEPTED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Cannot block an accepted connection directly. " +
            "First remove the connection, then block the user separately."
        );
    }

    @Override
    public void remove(Connection connection) {
        log.info("Removing accepted connection: ID={}, User1={}, User2={}", 
            connection.getId(), connection.getRequesterId(), connection.getAddresseeId());
        
        // Removal is handled by deleting the record in the service layer
        // This method just validates that removal is allowed
        
        log.debug("Connection {} can be removed (will be deleted)", connection.getId());
        
        // Note: Event publishing is handled by ConnectionService layer
    }

    @Override
    public void cancel(Connection connection) {
        log.error("Cannot cancel ACCEPTED connection: ID={}", connection.getId());
        throw new UnsupportedOperationException(
            "Cannot cancel an accepted connection. Use remove() to disconnect."
        );
    }

    @Override
    public boolean canAccept(Connection connection) {
        return false; // Already accepted
    }

    @Override
    public boolean canReject(Connection connection) {
        return false; // Can't reject accepted connections
    }

    @Override
    public boolean canBlock(Connection connection) {
        return false; // Can't block directly (must remove first)
    }

    @Override
    public boolean canRemove(Connection connection) {
        return true; // ACCEPTED connections can be removed by either user
    }

    @Override
    public boolean canCancel(Connection connection) {
        return false; // Can't cancel (use remove instead)
    }

    @Override
    public ConnectionState getHandledState() {
        return ConnectionState.ACCEPTED;
    }

    @Override
    public boolean isTerminalState() {
        return true; // No state transitions (only deletion)
    }

    @Override
    public String getAllowedActionsDescription() {
        return "Allowed actions: remove (by either user to disconnect)";
    }
}

