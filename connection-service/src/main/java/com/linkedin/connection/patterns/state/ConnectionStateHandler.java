package com.linkedin.connection.patterns.state;

import com.linkedin.connection.model.Connection;

/**
 * State Pattern Interface for managing Connection state transitions.
 * 
 * Design Pattern: State Pattern
 * - Allows an object (Connection) to alter its behavior when its internal state changes
 * - Encapsulates state-specific behavior in separate handler classes
 * - Makes state transitions explicit and type-safe
 * 
 * Pattern Structure:
 * <pre>
 * ConnectionStateHandler (interface)
 *      ↑
 *      ├── PendingStateHandler (can accept/reject/block)
 *      ├── AcceptedStateHandler (can remove)
 *      ├── RejectedStateHandler (terminal)
 *      └── BlockedStateHandler (terminal)
 * </pre>
 * 
 * Benefits:
 * 1. Single Responsibility: Each handler handles one state
 * 2. Open/Closed: Easy to add new states without changing existing code
 * 3. Explicit Transitions: Clear methods instead of complex if/else chains
 * 4. Type Safety: Compile-time checking of allowed operations
 * 
 * Usage Example:
 * <pre>
 * {@code
 * Connection connection = // ... PENDING connection
 * ConnectionStateHandler handler = stateHandlerFactory.getHandler(connection.getState());
 * 
 * if (handler.canAccept(connection)) {
 *     handler.accept(connection);  // Transitions to ACCEPTED
 * }
 * }
 * </pre>
 * 
 * Real-World Analogy:
 * Like a traffic light:
 * - RED state: Can transition to GREEN (can't skip to YELLOW)
 * - GREEN state: Can transition to YELLOW
 * - YELLOW state: Can transition to RED
 * Each state knows its own valid transitions.
 * 
 * @see Connection
 * @see com.linkedin.connection.model.ConnectionState
 */
public interface ConnectionStateHandler {

    // =========================================================================
    // Action Methods - State Transitions
    // =========================================================================

    /**
     * Accepts a connection request.
     * Transitions state from PENDING to ACCEPTED.
     * 
     * Business Rules:
     * - Only PENDING connections can be accepted
     * - Only the addressee can accept
     * - Sets respondedAt timestamp
     * - Publishes ConnectionAcceptedEvent
     * 
     * @param connection The connection to accept
     * @throws UnsupportedOperationException if this state cannot accept
     * @throws IllegalStateException if business rules are violated
     */
    void accept(Connection connection);

    /**
     * Rejects a connection request.
     * Transitions state from PENDING to REJECTED.
     * 
     * Business Rules:
     * - Only PENDING connections can be rejected
     * - Only the addressee can reject
     * - Sets respondedAt timestamp
     * - Publishes ConnectionRejectedEvent
     * 
     * @param connection The connection to reject
     * @throws UnsupportedOperationException if this state cannot reject
     * @throws IllegalStateException if business rules are violated
     */
    void reject(Connection connection);

    /**
     * Blocks the requester.
     * Transitions state from PENDING to BLOCKED.
     * 
     * Business Rules:
     * - Only PENDING connections can be blocked
     * - Only the addressee can block
     * - Sets respondedAt timestamp
     * - Prevents future connection requests from requester
     * - Publishes ConnectionBlockedEvent
     * 
     * @param connection The connection to block
     * @throws UnsupportedOperationException if this state cannot block
     * @throws IllegalStateException if business rules are violated
     */
    void block(Connection connection);

    /**
     * Removes a connection.
     * Deletes the connection record from the database.
     * 
     * Business Rules:
     * - Only ACCEPTED connections can be removed
     * - Either user can remove
     * - Actually deletes the record (not a state transition)
     * - Publishes ConnectionRemovedEvent
     * 
     * @param connection The connection to remove
     * @throws UnsupportedOperationException if this state cannot remove
     * @throws IllegalStateException if business rules are violated
     */
    void remove(Connection connection);

    /**
     * Cancels a pending connection request.
     * Deletes the connection record from the database.
     * 
     * Business Rules:
     * - Only PENDING connections can be cancelled
     * - Only the requester can cancel
     * - Actually deletes the record (not a state transition)
     * - Publishes ConnectionCancelledEvent
     * 
     * @param connection The connection to cancel
     * @throws UnsupportedOperationException if this state cannot cancel
     * @throws IllegalStateException if business rules are violated
     */
    void cancel(Connection connection);

    // =========================================================================
    // Query Methods - Check Allowed Actions
    // =========================================================================

    /**
     * Checks if this state allows accepting.
     * 
     * @param connection The connection to check
     * @return true if accept is allowed, false otherwise
     */
    boolean canAccept(Connection connection);

    /**
     * Checks if this state allows rejecting.
     * 
     * @param connection The connection to check
     * @return true if reject is allowed, false otherwise
     */
    boolean canReject(Connection connection);

    /**
     * Checks if this state allows blocking.
     * 
     * @param connection The connection to check
     * @return true if block is allowed, false otherwise
     */
    boolean canBlock(Connection connection);

    /**
     * Checks if this state allows removing.
     * 
     * @param connection The connection to check
     * @return true if remove is allowed, false otherwise
     */
    boolean canRemove(Connection connection);

    /**
     * Checks if this state allows cancelling.
     * 
     * @param connection The connection to check
     * @return true if cancel is allowed, false otherwise
     */
    boolean canCancel(Connection connection);

    // =========================================================================
    // State Information
    // =========================================================================

    /**
     * Returns the state that this handler manages.
     * 
     * @return The ConnectionState this handler is for
     */
    com.linkedin.connection.model.ConnectionState getHandledState();

    /**
     * Checks if this is a terminal state (no further transitions possible).
     * 
     * @return true if terminal state (REJECTED, BLOCKED), false otherwise
     */
    boolean isTerminalState();

    /**
     * Gets a user-friendly description of allowed actions in this state.
     * Useful for error messages and documentation.
     * 
     * @return A string describing what actions are allowed
     */
    String getAllowedActionsDescription();
}

