package com.linkedin.connection.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA Entity representing a connection between two users.
 * 
 * A connection represents a professional relationship on the platform, similar to
 * LinkedIn connections. The lifecycle follows this pattern:
 * 1. User A (requester) sends a connection request to User B (addressee)
 * 2. Connection created with state = PENDING
 * 3. User B can accept, reject, or block
 * 4. State transitions to ACCEPTED, REJECTED, or BLOCKED
 * 
 * Database Design:
 * - Table: connections
 * - Primary Key: id (BIGSERIAL)
 * - Foreign Keys: requester_id, addressee_id (references users table in user-service DB)
 * - Indexes: On requester_id, addressee_id, state, and composite (requester_id, addressee_id)
 * - Unique Constraint: (requester_id, addressee_id) to prevent duplicate requests
 * - Check Constraint: requester_id != addressee_id (can't connect to self)
 * 
 * Bidirectional Consideration:
 * - A connection A→B is the same as B→A for query purposes
 * - Stored directionally (requester → addressee) but queried bidirectionally
 * - When querying "my connections", we search both requester_id and addressee_id
 * 
 * Design Patterns:
 * - Entity Pattern: Domain-driven design, rich domain model
 * - Audit Pattern: Automatic tracking of created/updated timestamps
 * - State Pattern: Connection state managed via ConnectionState enum
 * 
 * @see ConnectionState
 * @see com.linkedin.connection.repository.ConnectionRepository
 * @see com.linkedin.connection.service.ConnectionService
 */
@Entity
@Table(name = "connections", indexes = {
    @Index(name = "idx_connections_requester", columnList = "requester_id"),
    @Index(name = "idx_connections_addressee", columnList = "addressee_id"),
    @Index(name = "idx_connections_state", columnList = "state"),
    @Index(name = "idx_connections_requester_addressee", columnList = "requester_id, addressee_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_connections_requester_addressee", columnNames = {"requester_id", "addressee_id"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"message"}) // Exclude potentially long message field
public class Connection {

    // =========================================================================
    // Primary Key
    // =========================================================================
    
    /**
     * Primary key - Auto-generated unique identifier for the connection.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================================================================
    // Core Fields
    // =========================================================================
    
    /**
     * ID of the user who sent the connection request.
     * References the User entity in the user-service database.
     * 
     * Note: We store only the ID, not a JPA relationship, because:
     * - User data is in a different microservice (user-service)
     * - We use Feign client to fetch user details when needed
     * - This maintains microservice independence
     */
    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    /**
     * ID of the user who received the connection request.
     * References the User entity in the user-service database.
     */
    @Column(name = "addressee_id", nullable = false)
    private Long addresseeId;

    /**
     * Current state of the connection.
     * Stored as a string in the database (VARCHAR(20)).
     * Mapped to the ConnectionState enum in Java.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    @Builder.Default
    private ConnectionState state = ConnectionState.PENDING;

    /**
     * Timestamp when the connection request was sent.
     * Set when the connection is first created.
     */
    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    /**
     * Timestamp when the addressee responded to the request.
     * Null while PENDING, set when transitioning to ACCEPTED/REJECTED/BLOCKED.
     */
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    /**
     * Optional message from the requester explaining why they want to connect.
     * E.g., "I saw your talk at XYZ conference and would love to connect!"
     * 
     * Stored as TEXT in the database (unlimited length).
     */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    // =========================================================================
    // Audit Fields (Automatic via @EntityListeners)
    // =========================================================================
    
    /**
     * Timestamp when this connection record was created.
     * Automatically populated by JPA Auditing on INSERT.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when this connection record was last updated.
     * Automatically updated by JPA Auditing on UPDATE.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // =========================================================================
    // Helper Methods - User Identification
    // =========================================================================
    
    /**
     * Checks if the given user ID is the requester of this connection.
     * 
     * @param userId The user ID to check
     * @return true if the user is the requester, false otherwise
     */
    public boolean isRequester(Long userId) {
        return this.requesterId.equals(userId);
    }

    /**
     * Checks if the given user ID is the addressee of this connection.
     * 
     * @param userId The user ID to check
     * @return true if the user is the addressee, false otherwise
     */
    public boolean isAddressee(Long userId) {
        return this.addresseeId.equals(userId);
    }

    /**
     * Checks if the given user ID is involved in this connection (either side).
     * 
     * @param userId The user ID to check
     * @return true if the user is either requester or addressee
     */
    public boolean involvesUser(Long userId) {
        return isRequester(userId) || isAddressee(userId);
    }

    /**
     * Gets the ID of the other user in the connection (not the given user).
     * 
     * @param userId The user ID we know (either requester or addressee)
     * @return The ID of the other user, or null if userId is not involved
     */
    public Long getOtherUserId(Long userId) {
        if (isRequester(userId)) {
            return addresseeId;
        } else if (isAddressee(userId)) {
            return requesterId;
        }
        return null; // User not involved in this connection
    }

    // =========================================================================
    // Helper Methods - State Transitions
    // =========================================================================
    
    /**
     * Accepts the connection request.
     * Transitions state from PENDING to ACCEPTED and sets respondedAt timestamp.
     * 
     * @throws IllegalStateException if current state is not PENDING
     */
    public void accept() {
        this.state.validateTransition(ConnectionState.ACCEPTED);
        this.state = ConnectionState.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * Rejects the connection request.
     * Transitions state from PENDING to REJECTED and sets respondedAt timestamp.
     * 
     * @throws IllegalStateException if current state is not PENDING
     */
    public void reject() {
        this.state.validateTransition(ConnectionState.REJECTED);
        this.state = ConnectionState.REJECTED;
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * Blocks the requester.
     * Transitions state from PENDING to BLOCKED and sets respondedAt timestamp.
     * 
     * @throws IllegalStateException if current state is not PENDING
     */
    public void block() {
        this.state.validateTransition(ConnectionState.BLOCKED);
        this.state = ConnectionState.BLOCKED;
        this.respondedAt = LocalDateTime.now();
    }

    // =========================================================================
    // Helper Methods - State Checks
    // =========================================================================
    
    /**
     * Checks if the connection is currently pending.
     * @return true if state is PENDING
     */
    public boolean isPending() {
        return this.state == ConnectionState.PENDING;
    }

    /**
     * Checks if the connection is active (accepted).
     * @return true if state is ACCEPTED
     */
    public boolean isActive() {
        return this.state == ConnectionState.ACCEPTED;
    }

    /**
     * Checks if the connection was rejected.
     * @return true if state is REJECTED
     */
    public boolean isRejected() {
        return this.state == ConnectionState.REJECTED;
    }

    /**
     * Checks if the requester is blocked.
     * @return true if state is BLOCKED
     */
    public boolean isBlocked() {
        return this.state == ConnectionState.BLOCKED;
    }

    // =========================================================================
    // Validation - JPA Lifecycle Callbacks
    // =========================================================================
    
    /**
     * Validates the entity before persisting to the database.
     * Ensures business rules are enforced at the entity level.
     * 
     * @throws IllegalArgumentException if validation fails
     */
    @PrePersist
    @PreUpdate
    protected void validate() {
        // Rule 1: Cannot connect to self
        if (requesterId != null && requesterId.equals(addresseeId)) {
            throw new IllegalArgumentException("Cannot create a connection with the same user (requester_id = addressee_id)");
        }
        
        // Rule 2: Requester and addressee must be set
        if (requesterId == null) {
            throw new IllegalArgumentException("Requester ID cannot be null");
        }
        if (addresseeId == null) {
            throw new IllegalArgumentException("Addressee ID cannot be null");
        }
        
        // Rule 3: State must be set
        if (state == null) {
            throw new IllegalArgumentException("Connection state cannot be null");
        }
        
        // Rule 4: If not PENDING, respondedAt should be set
        if (state != ConnectionState.PENDING && respondedAt == null) {
            respondedAt = LocalDateTime.now(); // Auto-set if missing
        }
    }

    // =========================================================================
    // Business Logic Helpers
    // =========================================================================
    
    /**
     * Checks if the connection can be accepted by the given user.
     * Only the addressee can accept, and only if state is PENDING.
     * 
     * @param userId The user attempting to accept
     * @return true if the user can accept this connection
     */
    public boolean canBeAcceptedBy(Long userId) {
        return isAddressee(userId) && isPending();
    }

    /**
     * Checks if the connection can be rejected by the given user.
     * Only the addressee can reject, and only if state is PENDING.
     * 
     * @param userId The user attempting to reject
     * @return true if the user can reject this connection
     */
    public boolean canBeRejectedBy(Long userId) {
        return isAddressee(userId) && isPending();
    }

    /**
     * Checks if the connection can be cancelled by the given user.
     * Only the requester can cancel, and only if state is PENDING.
     * 
     * @param userId The user attempting to cancel
     * @return true if the user can cancel this connection
     */
    public boolean canBeCancelledBy(Long userId) {
        return isRequester(userId) && isPending();
    }

    /**
     * Checks if the connection can be removed by the given user.
     * Either user can remove an ACCEPTED connection.
     * 
     * @param userId The user attempting to remove
     * @return true if the user can remove this connection
     */
    public boolean canBeRemovedBy(Long userId) {
        return involvesUser(userId) && isActive();
    }
}

