package com.linkedin.connection.service;

import com.linkedin.common.exceptions.ResourceNotFoundException;
import com.linkedin.common.exceptions.ValidationException;
import com.linkedin.connection.dto.ConnectionRequestDto;
import com.linkedin.connection.dto.ConnectionResponseDto;
import com.linkedin.connection.dto.PendingRequestDto;
import com.linkedin.connection.event.*;
import com.linkedin.connection.mapper.ConnectionMapper;
import com.linkedin.connection.model.Connection;
import com.linkedin.connection.model.ConnectionState;
import com.linkedin.connection.patterns.state.ConnectionStateHandler;
import com.linkedin.connection.patterns.state.ConnectionStateHandlerFactory;
import com.linkedin.connection.repository.ConnectionRepository;
import com.linkedin.user.client.UserServiceClient;
import com.linkedin.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service class for managing professional connections between users.
 * This is the core business logic layer that orchestrates all connection-related operations.
 * 
 * Responsibilities:
 * 1. **Business Logic**: Implements connection workflows (send, accept, reject, etc.)
 * 2. **Validation**: Ensures business rules are enforced
 * 3. **Authorization**: Verifies users can perform requested actions
 * 4. **Orchestration**: Coordinates Repository, Mapper, State Handlers
 * 5. **Transaction Management**: Ensures data consistency with @Transactional
 * 6. **Caching**: Optimizes performance with Redis caching
 * 
 * Design Patterns Used:
 * - Service Layer Pattern: Separates business logic from presentation
 * - State Pattern: Delegates state transitions to handlers
 * - Repository Pattern: Abstracts data access
 * - DTO Pattern: Decouples API from domain model
 * 
 * Integration Points:
 * - ConnectionRepository: Data access
 * - ConnectionMapper: Entity ↔ DTO conversion
 * - ConnectionStateHandlerFactory: State transition logic
 * - (TODO) UserServiceClient: Verify user existence (Feign)
 * - (TODO) KafkaProducer: Publish events
 * - (TODO) RedisCache: Cache connection data
 * 
 * @see Connection
 * @see ConnectionRepository
 * @see ConnectionMapper
 * @see ConnectionStateHandlerFactory
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true) // Default to read-only transactions
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final ConnectionMapper connectionMapper;
    private final ConnectionStateHandlerFactory stateHandlerFactory;
    private final UserServiceClient userServiceClient;
    private final ConnectionEventPublisher eventPublisher;

    // =========================================================================
    // Create Connection - Send Request
    // =========================================================================

    /**
     * Sends a connection request from the current user to another user.
     * 
     * Business Rules:
     * 1. Cannot connect to yourself
     * 2. Cannot send duplicate requests
     * 3. Addressee must exist (verified via user-service)
     * 4. Initial state is PENDING
     * 
     * Flow:
     * 1. Validate request (self-connection, duplicate)
     * 2. Verify addressee exists (TODO: Feign call)
     * 3. Create Connection entity with state=PENDING
     * 4. Save to database
     * 5. Publish ConnectionRequestedEvent (TODO: Kafka)
     * 6. Return DTO to controller
     * 
     * @param requestDto DTO containing addressee ID and optional message
     * @param currentUserId ID of the user sending the request (from JWT)
     * @return ConnectionResponseDto with the created connection
     * @throws ValidationException if business rules are violated
     */
    @Transactional
    @CacheEvict(value = "userConnections", key = "#currentUserId")
    public ConnectionResponseDto sendConnectionRequest(ConnectionRequestDto requestDto, Long currentUserId) {
        log.info("User {} sending connection request to user {}", currentUserId, requestDto.getAddresseeId());

        // Business Rule 1: Cannot connect to yourself
        if (currentUserId.equals(requestDto.getAddresseeId())) {
            log.warn("User {} attempted to connect to themselves", currentUserId);
            throw new ValidationException(
                "Cannot send a connection request to yourself",
                "SELF_CONNECTION_NOT_ALLOWED"
            );
        }

        // Business Rule 2: Check for existing connection (any state)
        boolean connectionExists = connectionRepository.existsBetweenUsers(currentUserId, requestDto.getAddresseeId());
        if (connectionExists) {
            log.warn("Connection already exists between users {} and {}", currentUserId, requestDto.getAddresseeId());
            throw new ValidationException(
                "A connection request already exists between you and this user",
                "DUPLICATE_CONNECTION_REQUEST"
            );
        }

        // Business Rule 3: Verify addressee exists and is active
        log.debug("Validating addressee user {} via user-service", requestDto.getAddresseeId());
        UserResponse addressee = userServiceClient.getUserById(requestDto.getAddresseeId());
        if (!addressee.canReceiveConnectionRequests()) {
            log.warn("User {} attempted to connect to inactive user {}", currentUserId, requestDto.getAddresseeId());
            throw new ValidationException(
                "Cannot send connection request to this user",
                "USER_INACTIVE"
            );
        }
        log.debug("Addressee user {} validated successfully", requestDto.getAddresseeId());

        // Create Connection entity
        Connection connection = connectionMapper.toEntity(requestDto, currentUserId);
        
        // Save to database
        Connection savedConnection = connectionRepository.save(connection);
        log.info("Connection request created: ID={}, Requester={}, Addressee={}", 
            savedConnection.getId(), currentUserId, requestDto.getAddresseeId());

        // Publish event to Kafka
        ConnectionRequestedEvent event = new ConnectionRequestedEvent(
            savedConnection.getId(),
            savedConnection.getRequesterId(),
            savedConnection.getAddresseeId(),
            savedConnection.getMessage(),
            savedConnection.getRequestedAt()
        );
        eventPublisher.publishConnectionRequested(event);

        // Convert to DTO and return
        return connectionMapper.toResponseDto(savedConnection);
    }

    // =========================================================================
    // Accept Connection Request
    // =========================================================================

    /**
     * Accepts a pending connection request.
     * 
     * Business Rules:
     * 1. Connection must exist
     * 2. Connection must be in PENDING state
     * 3. Only the addressee can accept
     * 4. Transitions state to ACCEPTED
     * 
     * Authorization:
     * - Only the addressee (receiver of request) can accept
     * - Requester cannot accept their own request
     * 
     * @param connectionId ID of the connection to accept
     * @param currentUserId ID of the user accepting (from JWT)
     * @return ConnectionResponseDto with updated connection
     * @throws ResourceNotFoundException if connection not found
     * @throws ValidationException if not addressee or wrong state
     */
    @Transactional
    @CacheEvict(value = "userConnections", allEntries = true)
    public ConnectionResponseDto acceptConnectionRequest(Long connectionId, Long currentUserId) {
        log.info("User {} attempting to accept connection {}", currentUserId, connectionId);

        // Load connection from database
        Connection connection = connectionRepository.findById(connectionId)
            .orElseThrow(() -> {
                log.warn("Connection {} not found", connectionId);
                return new ResourceNotFoundException("Connection not found", "CONNECTION_NOT_FOUND");
            });

        // Authorization: Only addressee can accept
        if (!connection.canBeAcceptedBy(currentUserId)) {
            log.warn("User {} is not authorized to accept connection {}", currentUserId, connectionId);
            throw new ValidationException(
                "You are not authorized to accept this connection request. Only the recipient can accept.",
                "UNAUTHORIZED_ACTION"
            );
        }

        // Get the appropriate state handler
        ConnectionStateHandler handler = stateHandlerFactory.getHandler(connection.getState());

        // Check if accept is allowed in current state
        if (!handler.canAccept(connection)) {
            log.warn("Cannot accept connection {} in state {}", connectionId, connection.getState());
            throw new ValidationException(
                String.format("Cannot accept connection in %s state. %s", 
                    connection.getState(), handler.getAllowedActionsDescription()),
                "INVALID_STATE_TRANSITION"
            );
        }

        // Perform the state transition
        handler.accept(connection);

        // Save updated connection
        Connection updatedConnection = connectionRepository.save(connection);
        log.info("Connection {} accepted successfully by user {}", connectionId, currentUserId);

        // Publish event to Kafka
        ConnectionAcceptedEvent event = new ConnectionAcceptedEvent(
            updatedConnection.getId(),
            updatedConnection.getRequesterId(),
            updatedConnection.getAddresseeId(),
            updatedConnection.getRespondedAt()
        );
        eventPublisher.publishConnectionAccepted(event);

        // Convert to DTO and return
        return connectionMapper.toResponseDto(updatedConnection);
    }

    // =========================================================================
    // Reject Connection Request
    // =========================================================================

    /**
     * Rejects a pending connection request.
     * 
     * Business Rules:
     * 1. Connection must exist
     * 2. Connection must be in PENDING state
     * 3. Only the addressee can reject
     * 4. Transitions state to REJECTED
     * 
     * @param connectionId ID of the connection to reject
     * @param currentUserId ID of the user rejecting (from JWT)
     * @return ConnectionResponseDto with updated connection
     * @throws ResourceNotFoundException if connection not found
     * @throws ValidationException if not addressee or wrong state
     */
    @Transactional
    @CacheEvict(value = "userConnections", key = "#currentUserId")
    public ConnectionResponseDto rejectConnectionRequest(Long connectionId, Long currentUserId) {
        log.info("User {} attempting to reject connection {}", currentUserId, connectionId);

        // Load connection
        Connection connection = connectionRepository.findById(connectionId)
            .orElseThrow(() -> new ResourceNotFoundException("Connection not found", "CONNECTION_NOT_FOUND"));

        // Authorization: Only addressee can reject
        if (!connection.canBeRejectedBy(currentUserId)) {
            log.warn("User {} is not authorized to reject connection {}", currentUserId, connectionId);
            throw new ValidationException(
                "You are not authorized to reject this connection request",
                "UNAUTHORIZED_ACTION"
            );
        }

        // Get state handler and validate
        ConnectionStateHandler handler = stateHandlerFactory.getHandler(connection.getState());
        if (!handler.canReject(connection)) {
            throw new ValidationException(
                String.format("Cannot reject connection in %s state", connection.getState()),
                "INVALID_STATE_TRANSITION"
            );
        }

        // Perform state transition
        handler.reject(connection);

        // Save
        Connection updatedConnection = connectionRepository.save(connection);
        log.info("Connection {} rejected by user {}", connectionId, currentUserId);

        // Publish event to Kafka
        ConnectionRejectedEvent event = new ConnectionRejectedEvent(
            updatedConnection.getId(),
            updatedConnection.getRequesterId(),
            updatedConnection.getAddresseeId(),
            updatedConnection.getRespondedAt()
        );
        eventPublisher.publishConnectionRejected(event);

        return connectionMapper.toResponseDto(updatedConnection);
    }

    // =========================================================================
    // Block User
    // =========================================================================

    /**
     * Blocks a user by transitioning a pending request to BLOCKED state.
     * 
     * Blocking is the strongest form of rejection. It:
     * - Prevents future connection requests from the blocked user
     * - Transitions the connection to BLOCKED state
     * - Kept permanently for security
     * 
     * @param connectionId ID of the connection to block
     * @param currentUserId ID of the user blocking (from JWT)
     * @return ConnectionResponseDto with updated connection
     */
    @Transactional
    @CacheEvict(value = "userConnections", key = "#currentUserId")
    public ConnectionResponseDto blockUser(Long connectionId, Long currentUserId) {
        log.info("User {} attempting to block connection {}", currentUserId, connectionId);

        Connection connection = connectionRepository.findById(connectionId)
            .orElseThrow(() -> new ResourceNotFoundException("Connection not found", "CONNECTION_NOT_FOUND"));

        // Authorization: Only addressee can block
        if (!connection.isAddressee(currentUserId)) {
            throw new ValidationException(
                "Only the recipient of a connection request can block the sender",
                "UNAUTHORIZED_ACTION"
            );
        }

        // Get state handler and validate
        ConnectionStateHandler handler = stateHandlerFactory.getHandler(connection.getState());
        if (!handler.canBlock(connection)) {
            throw new ValidationException(
                String.format("Cannot block in %s state", connection.getState()),
                "INVALID_STATE_TRANSITION"
            );
        }

        // Perform state transition
        handler.block(connection);

        // Save
        Connection updatedConnection = connectionRepository.save(connection);
        log.warn("User {} blocked by user {}", connection.getRequesterId(), currentUserId);

        // Publish event to Kafka
        ConnectionBlockedEvent event = new ConnectionBlockedEvent(
            updatedConnection.getId(),
            updatedConnection.getRequesterId(),
            updatedConnection.getAddresseeId(),
            updatedConnection.getRespondedAt()
        );
        eventPublisher.publishConnectionBlocked(event);

        return connectionMapper.toResponseDto(updatedConnection);
    }

    // =========================================================================
    // Cancel Connection Request
    // =========================================================================

    /**
     * Cancels a pending connection request (sent by the current user).
     * This deletes the connection record.
     * 
     * Authorization:
     * - Only the requester (sender) can cancel
     * - Addressee cannot cancel (they should reject instead)
     * 
     * @param connectionId ID of the connection to cancel
     * @param currentUserId ID of the user cancelling (from JWT)
     * @throws ResourceNotFoundException if connection not found
     * @throws ValidationException if not requester or wrong state
     */
    @Transactional
    @CacheEvict(value = "userConnections", key = "#currentUserId")
    public void cancelConnectionRequest(Long connectionId, Long currentUserId) {
        log.info("User {} attempting to cancel connection {}", currentUserId, connectionId);

        Connection connection = connectionRepository.findById(connectionId)
            .orElseThrow(() -> new ResourceNotFoundException("Connection not found", "CONNECTION_NOT_FOUND"));

        // Authorization: Only requester can cancel
        if (!connection.canBeCancelledBy(currentUserId)) {
            throw new ValidationException(
                "Only the sender of a connection request can cancel it",
                "UNAUTHORIZED_ACTION"
            );
        }

        // Get state handler and validate
        ConnectionStateHandler handler = stateHandlerFactory.getHandler(connection.getState());
        if (!handler.canCancel(connection)) {
            throw new ValidationException(
                String.format("Cannot cancel connection in %s state", connection.getState()),
                "INVALID_STATE_TRANSITION"
            );
        }

        // Perform cancellation (validates state transition)
        handler.cancel(connection);

        // Delete the connection
        connectionRepository.delete(connection);
        log.info("Connection {} cancelled and deleted by user {}", connectionId, currentUserId);

        // Publish event to Kafka BEFORE deleting
        ConnectionCancelledEvent event = new ConnectionCancelledEvent(
            connection.getId(),
            connection.getRequesterId(),
            connection.getAddresseeId(),
            java.time.LocalDateTime.now()
        );
        eventPublisher.publishConnectionCancelled(event);
    }

    // =========================================================================
    // Remove Connection
    // =========================================================================

    /**
     * Removes an accepted connection (disconnects two users).
     * This deletes the connection record.
     * 
     * Authorization:
     * - Either user in an accepted connection can remove it
     * 
     * @param connectionId ID of the connection to remove
     * @param currentUserId ID of the user removing (from JWT)
     */
    @Transactional
    @CacheEvict(value = "userConnections", allEntries = true)
    public void removeConnection(Long connectionId, Long currentUserId) {
        log.info("User {} attempting to remove connection {}", currentUserId, connectionId);

        Connection connection = connectionRepository.findById(connectionId)
            .orElseThrow(() -> new ResourceNotFoundException("Connection not found", "CONNECTION_NOT_FOUND"));

        // Authorization: Either user can remove
        if (!connection.canBeRemovedBy(currentUserId)) {
            throw new ValidationException(
                "You are not authorized to remove this connection",
                "UNAUTHORIZED_ACTION"
            );
        }

        // Get state handler and validate
        ConnectionStateHandler handler = stateHandlerFactory.getHandler(connection.getState());
        if (!handler.canRemove(connection)) {
            throw new ValidationException(
                String.format("Cannot remove connection in %s state", connection.getState()),
                "INVALID_STATE_TRANSITION"
            );
        }

        // Perform removal (validates state)
        handler.remove(connection);

        // Delete the connection
        connectionRepository.delete(connection);
        log.info("Connection {} removed by user {}", connectionId, currentUserId);

        // Publish event to Kafka BEFORE deleting
        ConnectionRemovedEvent event = new ConnectionRemovedEvent(
            connection.getId(),
            connection.getRequesterId(),
            connection.getAddresseeId(),
            java.time.LocalDateTime.now(),
            currentUserId
        );
        eventPublisher.publishConnectionRemoved(event);
    }

    // =========================================================================
    // Query Methods - Get Connections
    // =========================================================================

    /**
     * Gets a specific connection by ID.
     * 
     * Authorization:
     * - Only users involved in the connection can view it
     * 
     * @param connectionId ID of the connection
     * @param currentUserId ID of the current user (from JWT)
     * @return ConnectionResponseDto
     */
    @Cacheable(value = "connections", key = "#connectionId")
    public ConnectionResponseDto getConnectionById(Long connectionId, Long currentUserId) {
        log.debug("User {} fetching connection {}", currentUserId, connectionId);

        Connection connection = connectionRepository.findById(connectionId)
            .orElseThrow(() -> new ResourceNotFoundException("Connection not found", "CONNECTION_NOT_FOUND"));

        // Authorization: Only involved users can view
        if (!connection.involvesUser(currentUserId)) {
            throw new ValidationException(
                "You are not authorized to view this connection",
                "UNAUTHORIZED_ACCESS"
            );
        }

        return connectionMapper.toResponseDto(connection);
    }

    /**
     * Gets all active connections for the current user.
     * Returns users that the current user is connected with.
     * 
     * @param currentUserId ID of the current user (from JWT)
     * @return List of ConnectionResponseDto (only ACCEPTED connections)
     */
    @Cacheable(value = "userConnections", key = "#currentUserId")
    public List<ConnectionResponseDto> getMyConnections(Long currentUserId) {
        log.debug("Fetching connections for user {}", currentUserId);

        List<Connection> connections = connectionRepository.findByUserIdAndState(currentUserId, ConnectionState.ACCEPTED);
        log.debug("Found {} connections for user {}", connections.size(), currentUserId);

        return connectionMapper.toResponseDtoList(connections);
    }

    /**
     * Gets all pending requests (sent and received) for the current user.
     * 
     * @param currentUserId ID of the current user (from JWT)
     * @return List of PendingRequestDto
     */
    public List<PendingRequestDto> getPendingRequests(Long currentUserId) {
        log.debug("Fetching pending requests for user {}", currentUserId);

        List<Connection> pendingConnections = connectionRepository.findByUserIdAndState(currentUserId, ConnectionState.PENDING);
        log.debug("Found {} pending requests for user {}", pendingConnections.size(), currentUserId);

        return pendingConnections.stream()
                .map(conn -> {
                    PendingRequestDto dto = connectionMapper.toPendingRequestDto(conn, currentUserId);
                    // Populate mutual connections count
                    Long otherUserId = conn.getOtherUserId(currentUserId);
                    int mutualCount = getMutualConnectionsCount(currentUserId, otherUserId);
                    dto.setMutualConnections(mutualCount);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Gets pending requests received by the current user (inbox).
     * These are requests that need action (accept/reject).
     * 
     * @param currentUserId ID of the current user (from JWT)
     * @return List of PendingRequestDto
     */
    public List<PendingRequestDto> getReceivedPendingRequests(Long currentUserId) {
        log.debug("Fetching received pending requests for user {}", currentUserId);

        List<Connection> received = connectionRepository.findByAddresseeIdAndState(currentUserId, ConnectionState.PENDING);
        log.debug("Found {} received pending requests for user {}", received.size(), currentUserId);

        return received.stream()
                .map(conn -> {
                    PendingRequestDto dto = connectionMapper.toPendingRequestDto(conn, currentUserId);
                    // Populate mutual connections count
                    int mutualCount = getMutualConnectionsCount(currentUserId, conn.getRequesterId());
                    dto.setMutualConnections(mutualCount);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Gets pending requests sent by the current user (outbox).
     * These are requests awaiting response.
     * 
     * @param currentUserId ID of the current user (from JWT)
     * @return List of PendingRequestDto
     */
    public List<PendingRequestDto> getSentPendingRequests(Long currentUserId) {
        log.debug("Fetching sent pending requests for user {}", currentUserId);

        List<Connection> sent = connectionRepository.findByRequesterIdAndState(currentUserId, ConnectionState.PENDING);
        log.debug("Found {} sent pending requests for user {}", sent.size(), currentUserId);

        return sent.stream()
                .map(conn -> {
                    PendingRequestDto dto = connectionMapper.toPendingRequestDto(conn, currentUserId);
                    // Populate mutual connections count
                    int mutualCount = getMutualConnectionsCount(currentUserId, conn.getAddresseeId());
                    dto.setMutualConnections(mutualCount);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Statistics Methods
    // =========================================================================

    /**
     * Gets the count of active connections for a user.
     * 
     * @param userId ID of the user
     * @return Count of active connections
     */
    @Cacheable(value = "connectionCounts", key = "#userId")
    public long getConnectionCount(Long userId) {
        log.debug("Counting connections for user {}", userId);
        return connectionRepository.countActiveConnectionsByUserId(userId);
    }

    /**
     * Checks if two users are connected.
     * 
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return true if users are connected, false otherwise
     */
    public boolean areUsersConnected(Long userId1, Long userId2) {
        log.debug("Checking if users {} and {} are connected", userId1, userId2);
        return connectionRepository.findActiveConnectionBetweenUsers(userId1, userId2).isPresent();
    }

    /**
     * Gets mutual connections between two users.
     * 
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return List of user IDs that are connected to both users
     */
    public List<Long> getMutualConnections(Long userId1, Long userId2) {
        log.debug("Finding mutual connections between users {} and {}", userId1, userId2);
        return connectionRepository.findMutualConnections(userId1, userId2);
    }

    /**
     * Gets the count of mutual connections between two users.
     * 
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return Count of mutual connections
     */
    public int getMutualConnectionsCount(Long userId1, Long userId2) {
        log.debug("Counting mutual connections between users {} and {}", userId1, userId2);
        return connectionRepository.findMutualConnections(userId1, userId2).size();
    }
}

