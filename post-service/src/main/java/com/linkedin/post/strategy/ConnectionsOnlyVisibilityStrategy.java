package com.linkedin.post.strategy;

import com.linkedin.post.model.Post;
import com.linkedin.post.model.PostVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy for CONNECTIONS_ONLY visibility posts.
 * 
 * Purpose:
 * Implements visibility logic for posts visible only to connections.
 * 
 * Design Pattern: Strategy Pattern (Concrete Strategy)
 * 
 * Business Rules:
 * CONNECTIONS_ONLY posts are visible to:
 * 1. The post author (always)
 * 2. 1st-degree connections of the author
 * 
 * NOT visible to:
 * - Non-connections
 * - Anonymous users
 * - 2nd-degree connections (friends of friends)
 * 
 * Use Cases:
 * - Personal career updates
 * - Semi-private thoughts
 * - Sharing within network
 * - Most common visibility setting
 * 
 * Example:
 * <pre>
 * User: John (ID=123)
 * Connections: [Sarah=456, Mike=789]
 * 
 * Post: "Just got promoted to Senior Engineer!"
 * Visibility: CONNECTIONS_ONLY
 * 
 * Who can see:
 * - John (author) ✅
 * - Sarah (connection) ✅
 * - Mike (connection) ✅
 * - Random user (not connection) ❌
 * - Anonymous user ❌
 * </pre>
 * 
 * Algorithm:
 * <pre>
 * canView(post, userId):
 *   if userId == post.authorId:
 *     return true  // Author can always see own post
 *   
 *   if userId is null:
 *     return false  // Anonymous users cannot see
 *   
 *   // Check if viewer is connected to author
 *   return connectionService.areConnected(userId, post.authorId)
 * </pre>
 * 
 * Inter-Service Communication:
 * This strategy calls Connection Service via Feign to check connection status.
 * 
 * @see VisibilityStrategy
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConnectionsOnlyVisibilityStrategy implements VisibilityStrategy {

    private final com.linkedin.post.client.ConnectionServiceClient connectionServiceClient;

    /**
     * Check if user can view connections-only post.
     * 
     * Logic:
     * 1. If user is the author → true (can always see own post)
     * 2. If user is null (anonymous) → false
     * 3. Check if user is connected to author via Connection Service
     * 
     * @param post The post to check (must have CONNECTIONS_ONLY visibility)
     * @param userId User ID (can be null for anonymous users)
     * @return true if user can view (author or connection)
     */
    @Override
    public boolean canView(Post post, Long userId) {
        log.debug("Checking CONNECTIONS_ONLY visibility for post {} by user {}", 
                post.getId(), userId);

        // 1. Author can always see their own post
        if (userId != null && userId.equals(post.getAuthorId())) {
            log.debug("User {} is the author of post {} - Access granted", userId, post.getId());
            return true;
        }

        // 2. Anonymous users cannot see connections-only posts
        if (userId == null) {
            log.debug("Anonymous user cannot view connections-only post {}", post.getId());
            return false;
        }

        // 3. Check if users are connected via Connection Service
        boolean areConnected;
        try {
            areConnected = connectionServiceClient.areConnected(userId, post.getAuthorId());
        } catch (Exception e) {
            log.error("Failed to check connection status between {} and {}: {}", 
                    userId, post.getAuthorId(), e.getMessage());
            // Fail closed: deny access if connection service is unavailable
            areConnected = false;
        }

        if (areConnected) {
            log.debug("User {} is connected to author {} - Access granted", 
                    userId, post.getAuthorId());
            return true;
        } else {
            log.debug("User {} is not connected to author {} - Access denied", 
                    userId, post.getAuthorId());
            return false;
        }
    }

    /**
     * Get supported visibility level.
     * 
     * @return PostVisibility.CONNECTIONS_ONLY
     */
    @Override
    public PostVisibility getSupportedVisibility() {
        return PostVisibility.CONNECTIONS_ONLY;
    }
}

