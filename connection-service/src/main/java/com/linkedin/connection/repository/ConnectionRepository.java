package com.linkedin.connection.repository;

import com.linkedin.connection.model.Connection;
import com.linkedin.connection.model.ConnectionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for managing {@link Connection} entities.
 * Provides standard CRUD operations and custom query methods for connection management.
 * 
 * Repository Pattern Benefits:
 * - Abstracts database access from business logic
 * - Provides type-safe query methods
 * - Auto-generates SQL based on method names (Spring Data magic!)
 * - Allows custom JPQL queries for complex operations
 * 
 * Query Optimization:
 * - All queries leverage the indexes created in V1__Create_connections_table.sql
 * - Bidirectional queries use composite indexes
 * - State filtering uses dedicated state index
 * 
 * Bidirectional Connection Concept:
 * - A connection A→B is functionally the same as B→A
 * - Stored directionally (requester → addressee) for audit trail
 * - Queried bidirectionally for user experience
 * - Example: "Show my connections" includes both sent and received
 * 
 * @see Connection
 * @see com.linkedin.connection.service.ConnectionService
 */
@Repository
public interface ConnectionRepository extends JpaRepository<Connection, Long> {

    // =========================================================================
    // Single User Lookup Methods
    // =========================================================================
    
    /**
     * Finds all connections where the user is the requester.
     * Use case: "Show all connection requests I sent"
     * 
     * Index used: idx_connections_requester
     * 
     * @param requesterId The ID of the requester
     * @return List of connections sent by the user
     */
    List<Connection> findByRequesterId(Long requesterId);
    
    /**
     * Finds all connections where the user is the addressee.
     * Use case: "Show all connection requests I received"
     * 
     * Index used: idx_connections_addressee
     * 
     * @param addresseeId The ID of the addressee
     * @return List of connections received by the user
     */
    List<Connection> findByAddresseeId(Long addresseeId);
    
    // =========================================================================
    // Bidirectional Lookup Methods (Either Requester or Addressee)
    // =========================================================================
    
    /**
     * Finds all connections involving a specific user (as either requester or addressee).
     * Use case: "Show all my connection requests (sent and received)"
     * 
     * Index used: idx_connections_requester OR idx_connections_addressee
     * 
     * @param userId The user ID to search for
     * @return List of connections where user is either requester or addressee
     */
    @Query("SELECT c FROM Connection c WHERE c.requesterId = :userId OR c.addresseeId = :userId")
    List<Connection> findByUserId(@Param("userId") Long userId);
    
    /**
     * Finds all connections involving a specific user with a specific state.
     * Use case: "Show all my active connections"
     * 
     * Index used: idx_connections_requester OR idx_connections_addressee + idx_connections_state
     * 
     * @param userId The user ID to search for
     * @param state The connection state to filter by
     * @return List of connections matching criteria
     */
    @Query("SELECT c FROM Connection c WHERE (c.requesterId = :userId OR c.addresseeId = :userId) AND c.state = :state")
    List<Connection> findByUserIdAndState(@Param("userId") Long userId, @Param("state") ConnectionState state);
    
    // =========================================================================
    // State-Based Query Methods
    // =========================================================================
    
    /**
     * Finds all connections with a specific state.
     * Use case: "Show all pending connections in the system" (admin view)
     * 
     * Index used: idx_connections_state
     * 
     * @param state The connection state to filter by
     * @return List of connections with the specified state
     */
    List<Connection> findByState(ConnectionState state);
    
    /**
     * Finds all pending requests sent by a specific user.
     * Use case: "Show all my pending sent requests"
     * 
     * Index used: idx_connections_requester + idx_connections_state
     * 
     * @param requesterId The ID of the requester
     * @param state The connection state (typically PENDING)
     * @return List of pending connections sent by the user
     */
    List<Connection> findByRequesterIdAndState(Long requesterId, ConnectionState state);
    
    /**
     * Finds all pending requests received by a specific user.
     * Use case: "Show all connection requests I need to respond to"
     * 
     * Index used: idx_connections_addressee_state (composite index)
     * 
     * @param addresseeId The ID of the addressee
     * @param state The connection state (typically PENDING)
     * @return List of pending connections received by the user
     */
    List<Connection> findByAddresseeIdAndState(Long addresseeId, ConnectionState state);
    
    // =========================================================================
    // Specific Connection Lookup
    // =========================================================================
    
    /**
     * Finds a connection between two specific users (directional).
     * Use case: "Check if User A has sent a request to User B"
     * 
     * Index used: idx_connections_requester_addressee (composite index)
     * 
     * @param requesterId The ID of the requester
     * @param addresseeId The ID of the addressee
     * @return Optional containing the connection if it exists
     */
    Optional<Connection> findByRequesterIdAndAddresseeId(Long requesterId, Long addresseeId);
    
    /**
     * Finds a connection between two users (bidirectional).
     * Checks both directions: A→B or B→A
     * Use case: "Is there any connection between User A and User B?"
     * 
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return Optional containing the connection if it exists (either direction)
     */
    @Query("SELECT c FROM Connection c WHERE " +
           "(c.requesterId = :userId1 AND c.addresseeId = :userId2) OR " +
           "(c.requesterId = :userId2 AND c.addresseeId = :userId1)")
    Optional<Connection> findByUserIds(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
    
    /**
     * Finds an active (ACCEPTED) connection between two users (bidirectional).
     * Use case: "Are User A and User B connected?"
     * 
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return Optional containing the connection if users are connected
     */
    @Query("SELECT c FROM Connection c WHERE " +
           "((c.requesterId = :userId1 AND c.addresseeId = :userId2) OR " +
           "(c.requesterId = :userId2 AND c.addresseeId = :userId1)) AND " +
           "c.state = 'ACCEPTED'")
    Optional<Connection> findActiveConnectionBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
    
    // =========================================================================
    // Existence Check Methods (Performance Optimized)
    // =========================================================================
    
    /**
     * Checks if a connection exists between two users (directional).
     * More efficient than findByRequesterIdAndAddresseeId when you only need to know if it exists.
     * 
     * @param requesterId The ID of the requester
     * @param addresseeId The ID of the addressee
     * @return true if connection exists, false otherwise
     */
    boolean existsByRequesterIdAndAddresseeId(Long requesterId, Long addresseeId);
    
    /**
     * Checks if any connection exists between two users (bidirectional).
     * More efficient than findByUserIds when you only need existence check.
     * 
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return true if any connection exists (either direction), false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Connection c WHERE " +
           "(c.requesterId = :userId1 AND c.addresseeId = :userId2) OR " +
           "(c.requesterId = :userId2 AND c.addresseeId = :userId1)")
    boolean existsBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
    
    // =========================================================================
    // Count Methods (Statistics and Analytics)
    // =========================================================================
    
    /**
     * Counts the number of active connections for a user.
     * Use case: "How many connections does User A have?"
     * 
     * @param userId The user ID
     * @return The count of active (ACCEPTED) connections
     */
    @Query("SELECT COUNT(c) FROM Connection c WHERE " +
           "(c.requesterId = :userId OR c.addresseeId = :userId) AND " +
           "c.state = 'ACCEPTED'")
    long countActiveConnectionsByUserId(@Param("userId") Long userId);
    
    /**
     * Counts pending requests received by a user.
     * Use case: "How many connection requests do I need to review?"
     * 
     * @param addresseeId The addressee user ID
     * @return The count of pending requests
     */
    long countByAddresseeIdAndState(Long addresseeId, ConnectionState state);
    
    /**
     * Counts pending requests sent by a user.
     * Use case: "How many pending requests have I sent?"
     * 
     * @param requesterId The requester user ID
     * @return The count of pending sent requests
     */
    long countByRequesterIdAndState(Long requesterId, ConnectionState state);
    
    // =========================================================================
    // Mutual Connections (Second-Degree Network)
    // =========================================================================
    
    /**
     * Finds mutual connections between two users.
     * Use case: "Show connections that User A and User B have in common"
     * 
     * Algorithm:
     * 1. Find all active connections of User A
     * 2. Find all active connections of User B
     * 3. Return the intersection (users connected to both A and B)
     * 
     * Note: This is a complex query. For performance, consider caching results in Redis.
     * 
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return List of user IDs that are connected to both users
     */
    @Query("SELECT DISTINCT " +
           "CASE " +
           "  WHEN c1.requesterId = :userId1 THEN c1.addresseeId " +
           "  ELSE c1.requesterId " +
           "END " +
           "FROM Connection c1 " +
           "JOIN Connection c2 ON " +
           "  ((c1.requesterId = :userId1 OR c1.addresseeId = :userId1) AND " +
           "   (c2.requesterId = :userId2 OR c2.addresseeId = :userId2) AND " +
           "   ((c1.requesterId = c2.requesterId AND c1.requesterId NOT IN (:userId1, :userId2)) OR " +
           "    (c1.requesterId = c2.addresseeId AND c1.requesterId NOT IN (:userId1, :userId2)) OR " +
           "    (c1.addresseeId = c2.requesterId AND c1.addresseeId NOT IN (:userId1, :userId2)) OR " +
           "    (c1.addresseeId = c2.addresseeId AND c1.addresseeId NOT IN (:userId1, :userId2)))) " +
           "WHERE c1.state = 'ACCEPTED' AND c2.state = 'ACCEPTED'")
    List<Long> findMutualConnections(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
    
    // =========================================================================
    // Cleanup and Maintenance Methods
    // =========================================================================
    
    /**
     * Finds old rejected or blocked connections for cleanup.
     * Use case: "Delete rejected connections older than 30 days"
     * 
     * Index used: idx_connections_state_created
     * 
     * @param states The states to search for (REJECTED, BLOCKED)
     * @param beforeDate The cutoff date
     * @return List of old connections to clean up
     */
    @Query("SELECT c FROM Connection c WHERE c.state IN :states AND c.createdAt < :beforeDate")
    List<Connection> findOldConnectionsByStates(@Param("states") List<ConnectionState> states, 
                                                 @Param("beforeDate") LocalDateTime beforeDate);
    
    /**
     * Deletes connections by state and creation date.
     * Use case: Scheduled cleanup job
     * 
     * @param states The states to delete
     * @param beforeDate The cutoff date
     * @return The number of deleted connections
     */
    @Query("DELETE FROM Connection c WHERE c.state IN :states AND c.createdAt < :beforeDate")
    int deleteOldConnectionsByStates(@Param("states") List<ConnectionState> states, 
                                      @Param("beforeDate") LocalDateTime beforeDate);
    
    // =========================================================================
    // Advanced Query Methods
    // =========================================================================
    
    /**
     * Finds all connections for a user ordered by most recent.
     * Use case: "Show my connection activity"
     * 
     * @param userId The user ID
     * @return List of connections ordered by updatedAt descending
     */
    @Query("SELECT c FROM Connection c WHERE c.requesterId = :userId OR c.addresseeId = :userId ORDER BY c.updatedAt DESC")
    List<Connection> findByUserIdOrderByUpdatedAtDesc(@Param("userId") Long userId);
    
    /**
     * Finds pending connections that haven't been responded to within a certain timeframe.
     * Use case: "Send reminder notifications for stale requests"
     * 
     * @param state The connection state (PENDING)
     * @param beforeDate The cutoff date
     * @return List of stale pending connections
     */
    @Query("SELECT c FROM Connection c WHERE c.state = :state AND c.requestedAt < :beforeDate AND c.respondedAt IS NULL")
    List<Connection> findStaleConnections(@Param("state") ConnectionState state, 
                                          @Param("beforeDate") LocalDateTime beforeDate);
}

