package com.linkedin.connection.controller;

import com.linkedin.common.dto.ApiResponse;
import com.linkedin.connection.dto.ConnectionRequestDto;
import com.linkedin.connection.dto.ConnectionResponseDto;
import com.linkedin.connection.dto.PendingRequestDto;
import com.linkedin.connection.dto.ConnectionSuggestionDto;
import com.linkedin.connection.service.ConnectionService;
import com.linkedin.connection.service.ConnectionSuggestionService;
import com.linkedin.connection.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing professional connections.
 * Exposes HTTP endpoints for connection-related operations.
 * 
 * Base Path: /api/connections
 * 
 * Endpoints:
 * - POST   /requests               - Send connection request
 * - GET    /requests/pending       - Get all pending requests
 * - GET    /requests/received      - Get received pending requests (inbox)
 * - GET    /requests/sent          - Get sent pending requests (outbox)
 * - PUT    /{id}/accept            - Accept connection request
 * - PUT    /{id}/reject            - Reject connection request
 * - PUT    /{id}/block             - Block user
 * - DELETE /{id}/cancel            - Cancel pending request
 * - DELETE /{id}                   - Remove connection
 * - GET    /{id}                   - Get specific connection
 * - GET    /list                   - Get my connections
 * - GET    /count                  - Get connection count
 * - GET    /check/{userId}         - Check if connected with user
 * - GET    /mutual/{userId}        - Get mutual connections
 * 
 * Authentication:
 * - All endpoints require JWT authentication (except documentation)
 * - Current user is extracted from SecurityContext
 * 
 * Response Format:
 * - All responses wrapped in ApiResponse<T> for consistency
 * - Success: HTTP 200/201 with data
 * - Error: HTTP 4xx/5xx with error details (handled by GlobalExceptionHandler)
 * 
 * @see ConnectionService
 * @see ConnectionRequestDto
 * @see ConnectionResponseDto
 * @see PendingRequestDto
 */
@RestController
@RequestMapping("/api/connections")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Connections", description = "APIs for managing professional connections between users")
@SecurityRequirement(name = "bearerAuth")
public class ConnectionController {

    private final ConnectionService connectionService;
    private final ConnectionSuggestionService suggestionService;
    private final JwtUtil jwtUtil;

    // =========================================================================
    // Create Connection - Send Request
    // =========================================================================

    /**
     * Sends a connection request to another user.
     * 
     * @param requestDto DTO containing addressee ID and optional message
     * @return ResponseEntity with created connection
     */
    @Operation(
        summary = "Send connection request",
        description = "Send a connection request to another user. The request will be in PENDING state until the recipient responds.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "Connection request sent successfully",
                content = @Content(schema = @Schema(implementation = ConnectionResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid request (self-connection, duplicate request, etc.)",
                content = @Content(schema = @Schema(implementation = com.linkedin.common.dto.ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Unauthorized - Invalid or missing JWT token"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Addressee user not found"
            )
        }
    )
    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<ConnectionResponseDto>> sendConnectionRequest(
            @Valid @RequestBody ConnectionRequestDto requestDto) {
        
        Long currentUserId = getCurrentUserId();
        log.info("User {} sending connection request to user {}", currentUserId, requestDto.getAddresseeId());
        
        ConnectionResponseDto response = connectionService.sendConnectionRequest(requestDto, currentUserId);
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Connection request sent successfully"));
    }

    // =========================================================================
    // Accept/Reject/Block Connection Request
    // =========================================================================

    /**
     * Accepts a pending connection request.
     * Only the addressee (recipient) can accept.
     * 
     * @param connectionId ID of the connection to accept
     * @return ResponseEntity with updated connection
     */
    @Operation(
        summary = "Accept connection request",
        description = "Accept a pending connection request. Only the recipient of the request can accept it. The connection state will change from PENDING to ACCEPTED.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Connection request accepted successfully",
                content = @Content(schema = @Schema(implementation = ConnectionResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid state transition or not authorized"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Connection not found"
            )
        }
    )
    @PutMapping("/{connectionId}/accept")
    public ResponseEntity<ApiResponse<ConnectionResponseDto>> acceptConnectionRequest(
            @Parameter(description = "ID of the connection to accept", required = true)
            @PathVariable Long connectionId) {
        
        Long currentUserId = getCurrentUserId();
        log.info("User {} accepting connection {}", currentUserId, connectionId);
        
        ConnectionResponseDto response = connectionService.acceptConnectionRequest(connectionId, currentUserId);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Connection request accepted successfully"));
    }

    /**
     * Rejects a pending connection request.
     * Only the addressee (recipient) can reject.
     * 
     * @param connectionId ID of the connection to reject
     * @return ResponseEntity with updated connection
     */
    @Operation(
        summary = "Reject connection request",
        description = "Reject a pending connection request. Only the recipient can reject. The connection state will change to REJECTED.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Connection request rejected successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid state transition or not authorized"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Connection not found"
            )
        }
    )
    @PutMapping("/{connectionId}/reject")
    public ResponseEntity<ApiResponse<ConnectionResponseDto>> rejectConnectionRequest(
            @Parameter(description = "ID of the connection to reject", required = true)
            @PathVariable Long connectionId) {
        
        Long currentUserId = getCurrentUserId();
        log.info("User {} rejecting connection {}", currentUserId, connectionId);
        
        ConnectionResponseDto response = connectionService.rejectConnectionRequest(connectionId, currentUserId);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Connection request rejected successfully"));
    }

    /**
     * Blocks the sender of a connection request.
     * Only the addressee can block. This is the strongest form of rejection.
     * 
     * @param connectionId ID of the connection to block
     * @return ResponseEntity with updated connection
     */
    @Operation(
        summary = "Block user",
        description = "Block the sender of a connection request. This prevents future connection requests from this user. Only the recipient can block.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "User blocked successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid state transition or not authorized"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Connection not found"
            )
        }
    )
    @PutMapping("/{connectionId}/block")
    public ResponseEntity<ApiResponse<ConnectionResponseDto>> blockUser(
            @Parameter(description = "ID of the connection to block", required = true)
            @PathVariable Long connectionId) {
        
        Long currentUserId = getCurrentUserId();
        log.info("User {} blocking connection {}", currentUserId, connectionId);
        
        ConnectionResponseDto response = connectionService.blockUser(connectionId, currentUserId);
        
        return ResponseEntity.ok(ApiResponse.success(response, "User blocked successfully"));
    }

    // =========================================================================
    // Cancel/Remove Connection
    // =========================================================================

    /**
     * Cancels a pending connection request.
     * Only the requester (sender) can cancel their own request.
     * 
     * @param connectionId ID of the connection to cancel
     * @return ResponseEntity with success message
     */
    @Operation(
        summary = "Cancel connection request",
        description = "Cancel a pending connection request that you sent. Only the sender can cancel. The connection will be deleted.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Connection request cancelled successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid state or not authorized"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Connection not found"
            )
        }
    )
    @DeleteMapping("/{connectionId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelConnectionRequest(
            @Parameter(description = "ID of the connection to cancel", required = true)
            @PathVariable Long connectionId) {
        
        Long currentUserId = getCurrentUserId();
        log.info("User {} cancelling connection {}", currentUserId, connectionId);
        
        connectionService.cancelConnectionRequest(connectionId, currentUserId);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Connection request cancelled successfully"));
    }

    /**
     * Removes an accepted connection (disconnect).
     * Either user in the connection can remove it.
     * 
     * @param connectionId ID of the connection to remove
     * @return ResponseEntity with success message
     */
    @Operation(
        summary = "Remove connection",
        description = "Remove an accepted connection (disconnect from the user). Either user can remove the connection. The connection will be deleted.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Connection removed successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid state or not authorized"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Connection not found"
            )
        }
    )
    @DeleteMapping("/{connectionId}")
    public ResponseEntity<ApiResponse<Void>> removeConnection(
            @Parameter(description = "ID of the connection to remove", required = true)
            @PathVariable Long connectionId) {
        
        Long currentUserId = getCurrentUserId();
        log.info("User {} removing connection {}", currentUserId, connectionId);
        
        connectionService.removeConnection(connectionId, currentUserId);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Connection removed successfully"));
    }

    // =========================================================================
    // Query Connections
    // =========================================================================

    /**
     * Gets a specific connection by ID.
     * 
     * @param connectionId ID of the connection
     * @return ResponseEntity with connection details
     */
    @Operation(
        summary = "Get connection by ID",
        description = "Get details of a specific connection. Only users involved in the connection can view it.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Connection found",
                content = @Content(schema = @Schema(implementation = ConnectionResponseDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Not authorized to view this connection"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Connection not found"
            )
        }
    )
    @GetMapping("/{connectionId}")
    public ResponseEntity<ApiResponse<ConnectionResponseDto>> getConnectionById(
            @Parameter(description = "ID of the connection", required = true)
            @PathVariable Long connectionId) {
        
        Long currentUserId = getCurrentUserId();
        log.debug("User {} fetching connection {}", currentUserId, connectionId);
        
        ConnectionResponseDto response = connectionService.getConnectionById(connectionId, currentUserId);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Connection retrieved successfully"));
    }

    /**
     * Gets all active connections for the current user.
     * 
     * @return ResponseEntity with list of connections
     */
    @Operation(
        summary = "Get my connections",
        description = "Get all users that you are connected with (ACCEPTED connections only).",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Connections retrieved successfully",
                content = @Content(schema = @Schema(implementation = ConnectionResponseDto.class))
            )
        }
    )
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<ConnectionResponseDto>>> getMyConnections() {
        Long currentUserId = getCurrentUserId();
        log.debug("User {} fetching their connections", currentUserId);
        
        List<ConnectionResponseDto> connections = connectionService.getMyConnections(currentUserId);
        
        return ResponseEntity.ok(ApiResponse.success(connections, 
            String.format("Found %d connections", connections.size())));
    }

    /**
     * Gets all pending connection requests (sent and received).
     * 
     * @return ResponseEntity with list of pending requests
     */
    @Operation(
        summary = "Get all pending requests",
        description = "Get all pending connection requests (both sent and received).",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Pending requests retrieved successfully",
                content = @Content(schema = @Schema(implementation = PendingRequestDto.class))
            )
        }
    )
    @GetMapping("/requests/pending")
    public ResponseEntity<ApiResponse<List<PendingRequestDto>>> getPendingRequests() {
        Long currentUserId = getCurrentUserId();
        log.debug("User {} fetching pending requests", currentUserId);
        
        List<PendingRequestDto> requests = connectionService.getPendingRequests(currentUserId);
        
        return ResponseEntity.ok(ApiResponse.success(requests, 
            String.format("Found %d pending requests", requests.size())));
    }

    /**
     * Gets pending requests received by the current user (inbox).
     * 
     * @return ResponseEntity with list of received pending requests
     */
    @Operation(
        summary = "Get received pending requests",
        description = "Get pending connection requests that you received (inbox). These require your action (accept/reject).",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Received requests retrieved successfully"
            )
        }
    )
    @GetMapping("/requests/received")
    public ResponseEntity<ApiResponse<List<PendingRequestDto>>> getReceivedPendingRequests() {
        Long currentUserId = getCurrentUserId();
        log.debug("User {} fetching received pending requests", currentUserId);
        
        List<PendingRequestDto> requests = connectionService.getReceivedPendingRequests(currentUserId);
        
        return ResponseEntity.ok(ApiResponse.success(requests, 
            String.format("Found %d received pending requests", requests.size())));
    }

    /**
     * Gets pending requests sent by the current user (outbox).
     * 
     * @return ResponseEntity with list of sent pending requests
     */
    @Operation(
        summary = "Get sent pending requests",
        description = "Get pending connection requests that you sent (outbox). These are awaiting response from recipients.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Sent requests retrieved successfully"
            )
        }
    )
    @GetMapping("/requests/sent")
    public ResponseEntity<ApiResponse<List<PendingRequestDto>>> getSentPendingRequests() {
        Long currentUserId = getCurrentUserId();
        log.debug("User {} fetching sent pending requests", currentUserId);
        
        List<PendingRequestDto> requests = connectionService.getSentPendingRequests(currentUserId);
        
        return ResponseEntity.ok(ApiResponse.success(requests, 
            String.format("Found %d sent pending requests", requests.size())));
    }

    // =========================================================================
    // Statistics and Checks
    // =========================================================================

    /**
     * Gets the count of active connections for the current user.
     * 
     * @return ResponseEntity with connection count
     */
    @Operation(
        summary = "Get connection count",
        description = "Get the total number of active connections for the current user.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Connection count retrieved successfully"
            )
        }
    )
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> getConnectionCount() {
        Long currentUserId = getCurrentUserId();
        log.debug("User {} fetching connection count", currentUserId);
        
        long count = connectionService.getConnectionCount(currentUserId);
        
        return ResponseEntity.ok(ApiResponse.success(count, 
            String.format("You have %d connections", count)));
    }

    /**
     * Checks if the current user is connected with another user.
     * 
     * @param userId ID of the other user
     * @return ResponseEntity with boolean result
     */
    @Operation(
        summary = "Check if connected",
        description = "Check if you are connected with a specific user.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Check completed successfully"
            )
        }
    )
    @GetMapping("/check/{userId}")
    public ResponseEntity<ApiResponse<Boolean>> checkIfConnected(
            @Parameter(description = "ID of the user to check", required = true)
            @PathVariable Long userId) {
        
        Long currentUserId = getCurrentUserId();
        log.debug("Checking if users {} and {} are connected", currentUserId, userId);
        
        boolean isConnected = connectionService.areUsersConnected(currentUserId, userId);
        
        return ResponseEntity.ok(ApiResponse.success(isConnected, 
            isConnected ? "You are connected" : "You are not connected"));
    }

    /**
     * Gets mutual connections between the current user and another user.
     * 
     * @param userId ID of the other user
     * @return ResponseEntity with list of mutual connection user IDs
     */
    @Operation(
        summary = "Get mutual connections",
        description = "Get a list of users who are connected to both you and another specified user.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Mutual connections retrieved successfully"
            )
        }
    )
    @GetMapping("/mutual/{userId}")
    public ResponseEntity<ApiResponse<List<Long>>> getMutualConnections(
            @Parameter(description = "ID of the other user", required = true)
            @PathVariable Long userId) {
        
        Long currentUserId = getCurrentUserId();
        log.debug("Fetching mutual connections between users {} and {}", currentUserId, userId);
        
        List<Long> mutualConnections = connectionService.getMutualConnections(currentUserId, userId);
        
        return ResponseEntity.ok(ApiResponse.success(mutualConnections, 
            String.format("Found %d mutual connections", mutualConnections.size())));
    }

    // =========================================================================
    // Connection Suggestions (People You May Know)
    // =========================================================================

    /**
     * Get connection suggestions for the current user ("People You May Know").
     * Uses multiple strategies (mutual connections, same industry, etc.) to generate suggestions.
     * 
     * @param limit Maximum number of suggestions to return (default: 10, max: 50)
     * @return List of suggested connections with reasons
     */
    @Operation(
        summary = "Get connection suggestions",
        description = "Returns personalized connection suggestions based on mutual connections, " +
                      "industry, location, and other factors. Also known as 'People You May Know'.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved suggestions",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
        }
    )
    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<ConnectionSuggestionDto>>> getConnectionSuggestions(
            @Parameter(description = "Maximum number of suggestions (default: 10, max: 50)")
            @RequestParam(defaultValue = "10") int limit) {
        
        // Validate limit
        if (limit < 1) {
            limit = 10;
        }
        if (limit > 50) {
            limit = 50;
        }
        
        Long currentUserId = getCurrentUserId();
        log.debug("Fetching {} connection suggestions for user {}", limit, currentUserId);
        
        List<ConnectionSuggestionDto> suggestions = suggestionService.getConnectionSuggestions(currentUserId, limit);
        
        return ResponseEntity.ok(ApiResponse.success(suggestions, 
            String.format("Found %d connection suggestions", suggestions.size())));
    }

    /**
     * Get connection suggestions from a specific strategy.
     * Useful for testing or showing strategy-specific suggestions.
     * 
     * @param strategyName Name of the strategy ("Mutual Connections", "Same Industry", etc.)
     * @param limit Maximum number of suggestions
     * @return List of suggestions from that strategy
     */
    @Operation(
        summary = "Get suggestions by strategy",
        description = "Returns connection suggestions from a specific strategy. " +
                      "Available strategies: 'Mutual Connections', 'Same Industry', etc.",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved suggestions",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
        }
    )
    @GetMapping("/suggestions/strategy/{strategyName}")
    public ResponseEntity<ApiResponse<List<ConnectionSuggestionDto>>> getSuggestionsByStrategy(
            @Parameter(description = "Strategy name", required = true)
            @PathVariable String strategyName,
            @Parameter(description = "Maximum number of suggestions (default: 10)")
            @RequestParam(defaultValue = "10") int limit) {
        
        Long currentUserId = getCurrentUserId();
        log.debug("Fetching suggestions for user {} using strategy '{}'", currentUserId, strategyName);
        
        List<ConnectionSuggestionDto> suggestions = 
            suggestionService.getSuggestionsByStrategy(currentUserId, strategyName, limit);
        
        return ResponseEntity.ok(ApiResponse.success(suggestions, 
            String.format("Found %d suggestions using '%s' strategy", suggestions.size(), strategyName)));
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Extracts the current user ID from the JWT token in the SecurityContext.
     * 
     * This method delegates to JwtUtil which:
     * 1. Retrieves Authentication from SecurityContext
     * 2. Extracts JWT token from Authentication
     * 3. Parses JWT and extracts "userId" claim
     * 4. Returns the userId as Long
     * 
     * Example JWT Payload:
     * <pre>
     * {
     *   "sub": "john@example.com",
     *   "userId": 123,           ← This is what we extract
     *   "email": "john@example.com",
     *   "roles": ["USER"],
     *   "iat": 1639448000,
     *   "exp": 1639534400
     * }
     * </pre>
     * 
     * Security:
     * - JWT is validated by Spring Security before this point
     * - If JWT is invalid/expired, request is rejected at filter level
     * - This method assumes JWT is already validated
     * 
     * Error Handling:
     * - If no authentication: throws RuntimeException
     * - If userId not in JWT: throws RuntimeException
     * - These are caught by GlobalExceptionHandler
     * 
     * @return The ID of the currently authenticated user
     * @throws RuntimeException if no authenticated user or userId not found in JWT
     * @see com.linkedin.connection.security.JwtUtil#getCurrentUserId()
     */
    private Long getCurrentUserId() {
        return jwtUtil.getCurrentUserId();
    }
}

