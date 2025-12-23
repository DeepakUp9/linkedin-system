package com.linkedin.post.strategy;

import com.linkedin.post.model.Post;
import com.linkedin.post.model.PostVisibility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy for PUBLIC visibility posts.
 * 
 * Purpose:
 * Implements visibility logic for public posts.
 * 
 * Design Pattern: Strategy Pattern (Concrete Strategy)
 * 
 * Business Rule:
 * PUBLIC posts are visible to everyone:
 * - Logged-in users
 * - Anonymous users (not logged in)
 * - Non-connections
 * - Search engines (if indexed)
 * 
 * Use Cases:
 * - Company announcements
 * - Public thought leadership
 * - Job postings
 * - Articles for broad audience
 * 
 * Example:
 * <pre>
 * Post: "We're hiring! Join our team at Google."
 * Visibility: PUBLIC
 * Who can see: Everyone (billions of potential viewers)
 * </pre>
 * 
 * Algorithm:
 * <pre>
 * canView(post, userId):
 *   return true  // That's it! Always visible.
 * </pre>
 * 
 * @see VisibilityStrategy
 */
@Component
@Slf4j
public class PublicVisibilityStrategy implements VisibilityStrategy {

    /**
     * Check if user can view public post.
     * 
     * Logic: Always returns true.
     * Public posts are visible to everyone, no restrictions.
     * 
     * @param post The post to check (must have PUBLIC visibility)
     * @param userId User ID (can be null for anonymous users)
     * @return Always returns true
     */
    @Override
    public boolean canView(Post post, Long userId) {
        log.debug("Checking PUBLIC visibility for post {} - Always visible", post.getId());
        
        // Public posts are visible to everyone
        return true;
    }

    /**
     * Get supported visibility level.
     * 
     * @return PostVisibility.PUBLIC
     */
    @Override
    public PostVisibility getSupportedVisibility() {
        return PostVisibility.PUBLIC;
    }
}

