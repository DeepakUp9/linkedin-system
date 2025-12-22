package com.linkedin.notification.controller;

import com.linkedin.common.dto.ApiResponse;
import com.linkedin.notification.dto.MarkAsReadRequestDto;
import com.linkedin.notification.dto.NotificationPreferenceDto;
import com.linkedin.notification.dto.NotificationResponseDto;
import com.linkedin.notification.dto.NotificationStatsDto;
import com.linkedin.notification.service.NotificationPreferenceService;
import com.linkedin.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for notification management.
 * 
 * Purpose:
 * Provides HTTP API endpoints for:
 * - Viewing notifications (paginated)
 * - Marking notifications as read
 * - Getting notification statistics (unread count)
 * - Managing notification preferences
 * - Deleting notifications
 * 
 * Base URL: /api/notifications
 * 
 * Security:
 * - All endpoints require JWT authentication
 * - User can only access their own notifications
 * - User ID extracted from JWT token
 * 
 * Example Frontend Usage:
 * <pre>
 * // Get unread notifications
 * fetch('/api/notifications/unread', {
 *   headers: { 'Authorization': 'Bearer ' + jwtToken }
 * })
 * .then(res => res.json())
 * .then(notifications => {
 *   // Display in notification dropdown
 *   notifications.forEach(n => {
 *     showNotification(n.title, n.message);
 *   });
 * });
 * 
 * // Mark as read
 * fetch('/api/notifications/123/read', {
 *   method: 'POST',
 *   headers: { 'Authorization': 'Bearer ' + jwtToken }
 * });
 * </pre>
 * 
 * API Documentation:
 * - Swagger UI: http://localhost:8083/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8083/v3/api-docs
 * 
 * @see NotificationService
 * @see NotificationPreferenceService
 * @see NotificationResponseDto
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "APIs for managing user notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final com.linkedin.notification.config.JwtUtil jwtUtil;

    // =========================================================================
    // Notification Queries (GET)
    // =========================================================================

    /**
     * Get paginated list of notifications for authenticated user.
     * 
     * Endpoint: GET /api/notifications
     * 
     * Query Parameters:
     * - page: Page number (0-indexed, default: 0)
     * - size: Page size (default: 20)
     * - sort: Sort field (default: createdAt,desc)
     * 
     * Example:
     * GET /api/notifications?page=0&size=20&sort=createdAt,desc
     * 
     * Response:
     * {
     *   "status": "success",
     *   "message": "Retrieved 15 notifications",
     *   "data": {
     *     "content": [...notifications...],
     *     "totalElements": 150,
     *     "totalPages": 8,
     *     "number": 0,
     *     "size": 20
     *   }
     * }
     * 
     * @param page Page number (0-indexed)
     * @param size Page size
     * @param sortBy Sort field
     * @param sortDirection Sort direction (asc/desc)
     * @return Paginated notifications
     */
    @Operation(
        summary = "Get paginated notifications",
        description = "Retrieves a paginated list of all notifications for the authenticated user, sorted by creation date (newest first by default)"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved notifications",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Invalid or missing JWT token",
            content = @Content
        )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponseDto>>> getNotifications(
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0") int page,
        
        @Parameter(description = "Page size", example = "20")
        @RequestParam(defaultValue = "20") int size,
        
        @Parameter(description = "Sort field", example = "createdAt")
        @RequestParam(defaultValue = "createdAt") String sortBy,
        
        @Parameter(description = "Sort direction (asc/desc)", example = "desc")
        @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        Long userId = getCurrentUserId();
        log.debug("Fetching notifications for user {} (page={}, size={}, sort={},{})", 
            userId, page, size, sortBy, sortDirection);
        
        // Create pageable with sort
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        
        // Get notifications
        Page<NotificationResponseDto> notifications = notificationService.getNotifications(userId, pageable);
        
        String message = String.format("Retrieved %d notifications (page %d of %d)", 
            notifications.getNumberOfElements(), page + 1, notifications.getTotalPages());
        
        return ResponseEntity.ok(ApiResponse.success(notifications, message));
    }

    /**
     * Get unread notifications only.
     * 
     * Endpoint: GET /api/notifications/unread
     * 
     * Use Case:
     * Display unread notifications in dropdown menu
     * 
     * Response:
     * {
     *   "status": "success",
     *   "message": "Found 5 unread notifications",
     *   "data": [...notifications...]
     * }
     * 
     * @return List of unread notifications
     */
    @Operation(
        summary = "Get unread notifications",
        description = "Retrieves all unread in-app notifications for the authenticated user (not paginated, typically shown in notification dropdown)"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved unread notifications"
        )
    })
    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponseDto>>> getUnreadNotifications() {
        Long userId = getCurrentUserId();
        log.debug("Fetching unread notifications for user {}", userId);
        
        List<NotificationResponseDto> notifications = notificationService.getUnreadNotifications(userId);
        
        String message = String.format("Found %d unread notifications", notifications.size());
        return ResponseEntity.ok(ApiResponse.success(notifications, message));
    }

    /**
     * Get notification statistics (unread count, total count).
     * 
     * Endpoint: GET /api/notifications/stats
     * 
     * Use Case:
     * Display unread badge: 🔔 5
     * 
     * Response:
     * {
     *   "status": "success",
     *   "message": "Retrieved notification statistics",
     *   "data": {
     *     "totalNotifications": 150,
     *     "unreadCount": 5,
     *     "readCount": 145,
     *     "failedCount": 0
     *   }
     * }
     * 
     * @return Notification statistics
     */
    @Operation(
        summary = "Get notification statistics",
        description = "Retrieves aggregated statistics about user's notifications (total, unread, read counts)"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved statistics"
        )
    })
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<NotificationStatsDto>> getNotificationStats() {
        Long userId = getCurrentUserId();
        log.debug("Fetching notification stats for user {}", userId);
        
        NotificationStatsDto stats = notificationService.getNotificationStats(userId);
        
        return ResponseEntity.ok(ApiResponse.success(stats, "Retrieved notification statistics"));
    }

    /**
     * Get a single notification by ID.
     * 
     * Endpoint: GET /api/notifications/{id}
     * 
     * Security:
     * - Verifies notification belongs to authenticated user
     * - Returns 404 if not found or unauthorized
     * 
     * @param id Notification ID
     * @return Notification details
     */
    @Operation(
        summary = "Get notification by ID",
        description = "Retrieves a specific notification by its ID (must belong to authenticated user)"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved notification"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Notification not found or unauthorized",
            content = @Content
        )
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationResponseDto>> getNotificationById(
        @Parameter(description = "Notification ID", required = true, example = "123")
        @PathVariable Long id
    ) {
        Long userId = getCurrentUserId();
        log.debug("Fetching notification {} for user {}", id, userId);
        
        NotificationResponseDto notification = notificationService.getNotificationById(id, userId);
        
        return ResponseEntity.ok(ApiResponse.success(notification, "Notification retrieved successfully"));
    }

    // =========================================================================
    // Notification Updates (POST/PUT)
    // =========================================================================

    /**
     * Mark a single notification as read.
     * 
     * Endpoint: POST /api/notifications/{id}/read
     * 
     * Use Case:
     * User clicks on notification
     * 
     * Response:
     * {
     *   "status": "success",
     *   "message": "Notification marked as read",
     *   "data": {...updated notification...}
     * }
     * 
     * @param id Notification ID
     * @return Updated notification
     */
    @Operation(
        summary = "Mark notification as read",
        description = "Marks a single notification as read (only for in-app notifications)"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully marked as read"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Notification not found or unauthorized",
            content = @Content
        )
    })
    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponseDto>> markAsRead(
        @Parameter(description = "Notification ID", required = true, example = "123")
        @PathVariable Long id
    ) {
        Long userId = getCurrentUserId();
        log.info("User {} marking notification {} as read", userId, id);
        
        NotificationResponseDto notification = notificationService.markAsRead(id, userId);
        
        return ResponseEntity.ok(ApiResponse.success(notification, "Notification marked as read"));
    }

    /**
     * Mark multiple notifications as read (batch operation).
     * 
     * Endpoint: POST /api/notifications/mark-as-read
     * 
     * Request Body:
     * {
     *   "notificationIds": [1, 2, 3, 4, 5]
     * }
     * 
     * Use Case:
     * User selects multiple notifications and clicks "Mark as read"
     * 
     * @param request Contains list of notification IDs
     * @return Number of notifications marked as read
     */
    @Operation(
        summary = "Mark multiple notifications as read",
        description = "Marks multiple notifications as read in a single request (batch operation)"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully marked notifications as read"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request (empty notification IDs)",
            content = @Content
        )
    })
    @PostMapping("/mark-as-read")
    public ResponseEntity<ApiResponse<Integer>> markAsRead(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "List of notification IDs to mark as read",
            required = true
        )
        @Valid @RequestBody MarkAsReadRequestDto request
    ) {
        Long userId = getCurrentUserId();
        log.info("User {} marking {} notifications as read", userId, request.getNotificationIds().size());
        
        int count = notificationService.markAsRead(request, userId);
        
        String message = String.format("Marked %d notifications as read", count);
        return ResponseEntity.ok(ApiResponse.success(count, message));
    }

    /**
     * Mark ALL notifications as read.
     * 
     * Endpoint: POST /api/notifications/mark-all-as-read
     * 
     * Use Case:
     * User clicks "Mark all as read" button
     * 
     * Response:
     * {
     *   "status": "success",
     *   "message": "Marked 50 notifications as read",
     *   "data": 50
     * }
     * 
     * @return Number of notifications marked as read
     */
    @Operation(
        summary = "Mark all notifications as read",
        description = "Marks ALL unread in-app notifications as read for the authenticated user"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully marked all as read"
        )
    })
    @PostMapping("/mark-all-as-read")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead() {
        Long userId = getCurrentUserId();
        log.info("User {} marking all notifications as read", userId);
        
        int count = notificationService.markAllAsRead(userId);
        
        String message = String.format("Marked %d notifications as read", count);
        return ResponseEntity.ok(ApiResponse.success(count, message));
    }

    // =========================================================================
    // Notification Deletion (DELETE)
    // =========================================================================

    /**
     * Delete a notification.
     * 
     * Endpoint: DELETE /api/notifications/{id}
     * 
     * Use Case:
     * User dismisses notification
     * 
     * @param id Notification ID
     * @return Success message
     */
    @Operation(
        summary = "Delete notification",
        description = "Deletes a specific notification (must belong to authenticated user)"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully deleted notification"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Notification not found or unauthorized",
            content = @Content
        )
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
        @Parameter(description = "Notification ID", required = true, example = "123")
        @PathVariable Long id
    ) {
        Long userId = getCurrentUserId();
        log.info("User {} deleting notification {}", userId, id);
        
        notificationService.deleteNotification(id, userId);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Notification deleted successfully"));
    }

    /**
     * Delete all read notifications.
     * 
     * Endpoint: DELETE /api/notifications/read
     * 
     * Use Case:
     * User clicks "Clear all read notifications"
     * 
     * @return Number of notifications deleted
     */
    @Operation(
        summary = "Delete all read notifications",
        description = "Deletes all read notifications for the authenticated user"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully deleted read notifications"
        )
    })
    @DeleteMapping("/read")
    public ResponseEntity<ApiResponse<Integer>> deleteAllRead() {
        Long userId = getCurrentUserId();
        log.info("User {} deleting all read notifications", userId);
        
        int count = notificationService.deleteAllRead(userId);
        
        String message = String.format("Deleted %d read notifications", count);
        return ResponseEntity.ok(ApiResponse.success(count, message));
    }

    // =========================================================================
    // Notification Preferences (GET/PUT)
    // =========================================================================

    /**
     * Get notification preferences for authenticated user.
     * 
     * Endpoint: GET /api/notifications/preferences
     * 
     * Response:
     * {
     *   "status": "success",
     *   "message": "Retrieved preferences",
     *   "data": [
     *     {
     *       "notificationType": "CONNECTION_ACCEPTED",
     *       "emailEnabled": true,
     *       "inAppEnabled": true,
     *       "pushEnabled": false
     *     },
     *     ...
     *   ]
     * }
     * 
     * @return List of preferences for all notification types
     */
    @Operation(
        summary = "Get notification preferences",
        description = "Retrieves notification channel preferences for all notification types for the authenticated user"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved preferences"
        )
    })
    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<List<NotificationPreferenceDto>>> getPreferences() {
        Long userId = getCurrentUserId();
        log.debug("Fetching notification preferences for user {}", userId);
        
        List<NotificationPreferenceDto> preferences = preferenceService.getUserPreferences(userId);
        
        return ResponseEntity.ok(ApiResponse.success(preferences, "Retrieved notification preferences"));
    }

    /**
     * Update a notification preference.
     * 
     * Endpoint: PUT /api/notifications/preferences
     * 
     * Request Body:
     * {
     *   "notificationType": "CONNECTION_ACCEPTED",
     *   "emailEnabled": false,
     *   "inAppEnabled": true,
     *   "pushEnabled": false
     * }
     * 
     * Use Case:
     * User toggles "Email notifications for connection requests" in settings
     * 
     * @param preferenceDto Updated preference
     * @return Updated preference
     */
    @Operation(
        summary = "Update notification preference",
        description = "Updates notification channel preferences for a specific notification type"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully updated preference"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request (e.g., all channels disabled)",
            content = @Content
        )
    })
    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceDto>> updatePreference(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Updated notification preference",
            required = true
        )
        @Valid @RequestBody NotificationPreferenceDto preferenceDto
    ) {
        Long userId = getCurrentUserId();
        log.info("User {} updating preference for type {}", userId, preferenceDto.getNotificationType());
        
        NotificationPreferenceDto updated = preferenceService.updatePreference(userId, preferenceDto);
        
        return ResponseEntity.ok(ApiResponse.success(updated, "Preference updated successfully"));
    }

    /**
     * Batch update preferences.
     * 
     * Endpoint: PUT /api/notifications/preferences/batch
     * 
     * Request Body:
     * [
     *   {...preference 1...},
     *   {...preference 2...},
     *   {...preference 3...}
     * ]
     * 
     * Use Case:
     * User updates multiple preferences at once in settings page
     * 
     * @param preferences List of preferences to update
     * @return Updated preferences
     */
    @Operation(
        summary = "Batch update preferences",
        description = "Updates multiple notification preferences in a single request"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully updated preferences"
        )
    })
    @PutMapping("/preferences/batch")
    public ResponseEntity<ApiResponse<List<NotificationPreferenceDto>>> updatePreferences(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "List of preferences to update",
            required = true
        )
        @Valid @RequestBody List<NotificationPreferenceDto> preferences
    ) {
        Long userId = getCurrentUserId();
        log.info("User {} batch updating {} preferences", userId, preferences.size());
        
        List<NotificationPreferenceDto> updated = preferenceService.updatePreferences(userId, preferences);
        
        String message = String.format("Updated %d preferences", updated.size());
        return ResponseEntity.ok(ApiResponse.success(updated, message));
    }

    /**
     * Reset preferences to defaults.
     * 
     * Endpoint: POST /api/notifications/preferences/reset
     * 
     * Use Case:
     * User clicks "Reset to defaults" in settings
     * 
     * @return Success message
     */
    @Operation(
        summary = "Reset preferences to defaults",
        description = "Resets all notification preferences to their default values"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Successfully reset preferences"
        )
    })
    @PostMapping("/preferences/reset")
    public ResponseEntity<ApiResponse<Void>> resetPreferences() {
        Long userId = getCurrentUserId();
        log.info("User {} resetting preferences to defaults", userId);
        
        preferenceService.resetToDefaults(userId);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Preferences reset to defaults"));
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Extract current user ID from JWT token in SecurityContext.
     * 
     * How It Works:
     * 1. Request comes with JWT token in Authorization header
     * 2. Spring Security validates token (via JwtAuthenticationFilter)
     * 3. Stores user details in SecurityContext
     * 4. JwtUtil extracts user ID from token claims
     * 
     * Security:
     * - If no authentication, throws SecurityException
     * - User can only access their own data
     * 
     * @return Current user's ID
     * @throws SecurityException if user not authenticated
     */
    private Long getCurrentUserId() {
        return jwtUtil.getCurrentUserId();
    }
}

