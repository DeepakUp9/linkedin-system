package com.linkedin.connection.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Rich Enum representing the lifecycle states of a connection between two users.
 * 
 * Connection Lifecycle:
 * 1. User A sends request to User B → PENDING
 * 2. User B accepts request → ACCEPTED
 *    OR User B rejects request → REJECTED
 *    OR User B blocks User A → BLOCKED
 * 3. Either user can remove connection → back to no connection
 * 
 * State Transition Rules:
 * - PENDING → ACCEPTED (when addressee accepts)
 * - PENDING → REJECTED (when addressee rejects)
 * - PENDING → BLOCKED (when addressee blocks requester)
 * - ACCEPTED → removed (deleted from database)
 * - REJECTED → terminal state (can send new request later)
 * - BLOCKED → terminal state (no new requests allowed)
 * 
 * Design Pattern: Rich Enum
 * - Encapsulates behavior and validation within the enum itself
 * - Provides type-safe state management
 * - Self-documenting code
 * 
 * Integration with State Pattern:
 * - This enum defines the states
 * - ConnectionStateHandler implementations handle transitions
 * - Connection entity stores the current state
 * 
 * @see Connection
 * @see com.linkedin.connection.patterns.state.ConnectionStateHandler
 */
@Getter
public enum ConnectionState {

    /**
     * ACCEPTED: Connection request has been accepted, users are connected.
     * 
     * Possible transitions:
     * - None (can only be removed/deleted)
     * 
     * Business Rules:
     * - Both users can see each other in their connections list
     * - Either user can remove the connection (deletes the record)
     * - Users can now message each other, see full profiles, etc.
     */
    ACCEPTED(
        "Accepted",
        "Connection request accepted, users are now connected",
        false,  // canTransition (removal is deletion, not state change)
        true,   // isFinal (terminal state in the state machine)
        Set.of()  // No state transitions, only deletion
    ),
    
    /**
     * REJECTED: Connection request has been rejected.
     * 
     * Possible transitions:
     * - None (terminal state)
     * 
     * Business Rules:
     * - Requester cannot send another request immediately (cooldown period)
     * - Record kept for analytics and preventing spam
     * - Can be cleaned up after a period (e.g., 30 days)
     * - Addressee can later accept if requester sends a new request
     */
    REJECTED(
        "Rejected",
        "Connection request rejected",
        false,  // canTransition
        true,   // isFinal
        Set.of()  // No transitions
    ),
    
    /**
     * BLOCKED: User has blocked the other user.
     * 
     * Possible transitions:
     * - None (terminal state, requires manual unblock)
     * 
     * Business Rules:
     * - Blocked user cannot send connection requests
     * - Blocked user cannot see blocker's profile
     * - Requires admin intervention or manual unblock to reverse
     * - Strongest form of rejection
     */
    BLOCKED(
        "Blocked",
        "User has been blocked, no future connections allowed",
        false,  // canTransition
        true,   // isFinal
        Set.of()  // No transitions
    ),

    /**
     * PENDING: Connection request has been sent, awaiting response.
     *
     * Possible transitions:
     * - ACCEPTED: When addressee accepts the request
     * - REJECTED: When addressee rejects the request
     * - BLOCKED: When addressee blocks the requester
     *
     * Business Rules:
     * - Only the addressee can transition from PENDING
     * - Requester can cancel (delete) the pending request
     * - Cannot send duplicate requests while PENDING
     */

    PENDING(
        "Pending",
                "Connection request sent, awaiting response",
                true,   // canTransition
                false,  // isFinal
        Set.of(ACCEPTED, REJECTED, BLOCKED)  // allowedTransitions (forward reference)
    );
    // =========================================================================
    // Enum Fields
    // =========================================================================
    
    /**
     * Human-readable display name for UI.
     */
    private final String displayName;
    
    /**
     * Detailed description of the state.
     */
    private final String description;
    
    /**
     * Whether this state can transition to other states.
     */
    private final boolean canTransition;
    
    /**
     * Whether this is a final/terminal state in the state machine.
     */
    private final boolean isFinal;
    
    /**
     * Set of states this state can transition to.
     */
    private final Set<ConnectionState> allowedTransitions;
    
    // =========================================================================
    // Constructor
    // =========================================================================
    
    /**
     * Constructor for ConnectionState enum.
     * Note: Forward references to other enum constants are allowed here.
     * 
     * @param displayName Display name for UI
     * @param description Detailed description
     * @param canTransition Whether state can transition
     * @param isFinal Whether this is a terminal state
     * @param allowedTransitions Set of valid next states
     */
    ConnectionState(String displayName, String description, boolean canTransition, 
                    boolean isFinal, Set<ConnectionState> allowedTransitions) {
        this.displayName = displayName;
        this.description = description;
        this.canTransition = canTransition;
        this.isFinal = isFinal;
        this.allowedTransitions = allowedTransitions;
    }
    
    // =========================================================================
    // State Transition Methods
    // =========================================================================
    
    /**
     * Checks if a transition from this state to the target state is valid.
     * 
     * @param targetState The state to transition to
     * @return true if transition is allowed, false otherwise
     */
    public boolean canTransitionTo(ConnectionState targetState) {
        return allowedTransitions.contains(targetState);
    }
    
    /**
     * Validates a state transition and throws an exception if invalid.
     * 
     * @param targetState The state to transition to
     * @throws IllegalStateException if transition is not allowed
     */
    public void validateTransition(ConnectionState targetState) {
        if (!canTransitionTo(targetState)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s. Allowed transitions: %s",
                    this.name(), targetState.name(), allowedTransitions)
            );
        }
    }
    
    // =========================================================================
    // State Check Helper Methods
    // =========================================================================
    
    /**
     * Checks if this state is PENDING.
     * @return true if state is PENDING
     */
    public boolean isPending() {
        return this == PENDING;
    }
    
    /**
     * Checks if this state is ACCEPTED.
     * @return true if state is ACCEPTED
     */
    public boolean isAccepted() {
        return this == ACCEPTED;
    }
    
    /**
     * Checks if this state is REJECTED.
     * @return true if state is REJECTED
     */
    public boolean isRejected() {
        return this == REJECTED;
    }
    
    /**
     * Checks if this state is BLOCKED.
     * @return true if state is BLOCKED
     */
    public boolean isBlocked() {
        return this == BLOCKED;
    }
    
    /**
     * Checks if the connection is active (ACCEPTED state).
     * @return true if connection is active
     */
    public boolean isActive() {
        return this == ACCEPTED;
    }
    
    /**
     * Checks if the connection can be acted upon by the addressee.
     * @return true if state is PENDING (addressee can accept/reject)
     */
    public boolean requiresAction() {
        return this == PENDING;
    }
    
    // =========================================================================
    // Utility Methods
    // =========================================================================
    
    /**
     * Checks if a given state string is a valid ConnectionState.
     * 
     * @param stateString The string representation of the state
     * @return true if it's a valid state, false otherwise
     */
    public static boolean isValidState(String stateString) {
        return Arrays.stream(ConnectionState.values())
            .anyMatch(state -> state.name().equalsIgnoreCase(stateString));
    }
    
    /**
     * Converts a string representation to a ConnectionState enum.
     * 
     * @param stateString The string representation of the state
     * @return An Optional containing the ConnectionState if found, empty otherwise
     */
    public static Optional<ConnectionState> fromString(String stateString) {
        return Arrays.stream(ConnectionState.values())
            .filter(state -> state.name().equalsIgnoreCase(stateString))
            .findFirst();
    }
    
    /**
     * Returns a user-friendly message for this state.
     * Useful for API responses and notifications.
     * 
     * @return A formatted message describing the state
     */
    public String getUserMessage() {
        return switch (this) {
            case PENDING -> "Your connection request is pending approval.";
            case ACCEPTED -> "You are now connected!";
            case REJECTED -> "Your connection request was declined.";
            case BLOCKED -> "You cannot connect with this user.";
        };
    }
    
    /**
     * Returns the opposite perspective message (for the other user).
     * 
     * @return A message from the other user's perspective
     */
    public String getRecipientMessage() {
        return switch (this) {
            case PENDING -> "You have a pending connection request.";
            case ACCEPTED -> "Connection request accepted!";
            case REJECTED -> "You declined the connection request.";
            case BLOCKED -> "You have blocked this user.";
        };
    }
}

