package com.linkedin.post.model;

import lombok.Getter;

/**
 * Enum representing post visibility levels.
 * 
 * Purpose:
 * Controls who can see a post based on the author's privacy settings.
 * 
 * Visibility Levels:
 * 
 * 1. PUBLIC:
 *    - Anyone can see the post
 *    - Appears in search results
 *    - Visible to non-connections
 *    - Example: Company announcements, public thought leadership
 * 
 * 2. CONNECTIONS_ONLY:
 *    - Only 1st-degree connections can see
 *    - Does not appear in public search
 *    - Most common setting for personal posts
 *    - Example: Career updates, personal achievements
 * 
 * 3. PRIVATE:
 *    - Only visible to the author
 *    - Draft mode or personal notes
 *    - Can be changed to public later
 *    - Example: Drafts, private thoughts
 * 
 * Usage in Code:
 * <pre>
 * {@code
 * Post post = Post.builder()
 *     .content("New job announcement!")
 *     .visibility(PostVisibility.PUBLIC)
 *     .build();
 * 
 * // Check visibility
 * if (post.isVisibleTo(currentUserId)) {
 *     // Show post
 * }
 * }
 * </pre>
 * 
 * Business Rules:
 * - Default visibility: CONNECTIONS_ONLY
 * - Cannot change visibility if post has been shared
 * - PUBLIC posts may be indexed by search engines
 * 
 * @see Post#isVisibleTo(Long)
 */
@Getter
public enum PostVisibility {
    
    /**
     * Visible to everyone, including non-logged-in users.
     */
    PUBLIC("Public", "Anyone on or off LinkedIn", 0),
    
    /**
     * Visible only to 1st-degree connections.
     */
    CONNECTIONS_ONLY("Connections Only", "Only your connections", 1),
    
    /**
     * Visible only to the post author.
     */
    PRIVATE("Private", "Only you", 2);

    private final String displayName;
    private final String description;
    private final int restrictionLevel; // 0 = least restrictive, 2 = most restrictive

    PostVisibility(String displayName, String description, int restrictionLevel) {
        this.displayName = displayName;
        this.description = description;
        this.restrictionLevel = restrictionLevel;
    }

    /**
     * Check if this visibility is more restrictive than another.
     * 
     * @param other The other visibility level
     * @return true if this is more restrictive
     */
    public boolean isMoreRestrictiveThan(PostVisibility other) {
        return this.restrictionLevel > other.restrictionLevel;
    }

    /**
     * Check if this visibility allows public access.
     * 
     * @return true if PUBLIC
     */
    public boolean isPublic() {
        return this == PUBLIC;
    }

    /**
     * Check if this visibility requires connection check.
     * 
     * @return true if CONNECTIONS_ONLY or PRIVATE
     */
    public boolean requiresConnectionCheck() {
        return this == CONNECTIONS_ONLY || this == PRIVATE;
    }
}

