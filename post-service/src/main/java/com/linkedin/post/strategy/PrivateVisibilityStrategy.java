package com.linkedin.post.strategy;

import com.linkedin.post.model.Post;
import com.linkedin.post.model.PostVisibility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy for PRIVATE visibility posts.
 * 
 * Purpose:
 * Implements visibility logic for private posts.
 * 
 * Design Pattern: Strategy Pattern (Concrete Strategy)
 * 
 * Business Rule:
 * PRIVATE posts are visible ONLY to the post author.
 * 
 * Use Cases:
 * - Draft posts (work in progress)
 * - Personal notes
 * - Posts waiting to be published
 * - Private thoughts not ready to share
 * 
 * Example:
 * <pre>
 * User: John (ID=123)
 * 
 * Post: "Draft: My thoughts on microservices (not ready yet)"
 * Visibility: PRIVATE
 * 
 * Who can see:
 * - John (author) ✅
 * - Everyone else ❌
 * </pre>
 * 
 * Algorithm:
 * <pre>
 * canView(post, userId):
 *   if userId == null:
 *     return false  // Anonymous users cannot see
 *   
 *   return userId == post.authorId  // Only author
 * </pre>
 * 
 * Privacy Guarantee:
 * - No one except author can view
 * - Not visible in search
 * - Not visible in feeds
 * - Not visible to connections
 * - Not visible to admins (in typical implementation)
 * 
 * @see VisibilityStrategy
 */
@Component
@Slf4j
public class PrivateVisibilityStrategy implements VisibilityStrategy {

    /**
     * Check if user can view private post.
     * 
     * Logic:
     * Only the post author can view.
     * All other users (including connections) are denied.
     * 
     * @param post The post to check (must have PRIVATE visibility)
     * @param userId User ID (can be null for anonymous users)
     * @return true only if user is the post author
     */
    @Override
    public boolean canView(Post post, Long userId) {
        log.debug("Checking PRIVATE visibility for post {} by user {}", 
                post.getId(), userId);

        // Anonymous users cannot view private posts
        if (userId == null) {
            log.debug("Anonymous user cannot view private post {}", post.getId());
            return false;
        }

        // Only author can view
        boolean isAuthor = userId.equals(post.getAuthorId());
        
        if (isAuthor) {
            log.debug("User {} is the author of private post {} - Access granted", 
                    userId, post.getId());
        } else {
            log.debug("User {} is not the author of private post {} - Access denied", 
                    userId, post.getId());
        }

        return isAuthor;
    }

    /**
     * Get supported visibility level.
     * 
     * @return PostVisibility.PRIVATE
     */
    @Override
    public PostVisibility getSupportedVisibility() {
        return PostVisibility.PRIVATE;
    }
}

