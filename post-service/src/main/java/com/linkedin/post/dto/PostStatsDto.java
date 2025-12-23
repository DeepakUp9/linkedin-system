package com.linkedin.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for post statistics and analytics.
 * 
 * Purpose:
 * Aggregated statistics for a user's post activity.
 * Used in profile pages and analytics dashboards.
 * 
 * API Response Example:
 * <pre>
 * GET /api/users/{userId}/post-stats
 * 
 * {
 *   "totalPosts": 42,
 *   "totalLikesReceived": 1256,
 *   "totalCommentsReceived": 234,
 *   "totalSharesReceived": 67,
 *   "totalViewsReceived": 8934,
 *   "totalEngagement": 1557,
 *   "averageEngagementPerPost": 37,
 *   "mostLikedPostId": 123,
 *   "mostCommentedPostId": 456,
 *   "postsByType": {
 *     "TEXT": 30,
 *     "IMAGE": 10,
 *     "ARTICLE": 2,
 *     "VIDEO": 0,
 *     "POLL": 0
 *   },
 *   "postsByVisibility": {
 *     "PUBLIC": 15,
 *     "CONNECTIONS_ONLY": 25,
 *     "PRIVATE": 2
 *   }
 * }
 * </pre>
 * 
 * Use Cases:
 * - Profile statistics section
 * - User activity analytics
 * - Content creator dashboard
 * - Influencer metrics
 * 
 * @see com.linkedin.post.service.PostService#getUserStats
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostStatsDto {

    /**
     * Total number of posts created by user.
     */
    private Long totalPosts;

    /**
     * Total likes received across all posts.
     */
    private Long totalLikesReceived;

    /**
     * Total comments received across all posts.
     */
    private Long totalCommentsReceived;

    /**
     * Total shares received across all posts.
     */
    private Long totalSharesReceived;

    /**
     * Total views received across all posts.
     */
    private Long totalViewsReceived;

    /**
     * Total engagement (likes + comments + shares).
     */
    private Long totalEngagement;

    /**
     * Average engagement per post.
     */
    private Double averageEngagementPerPost;

    /**
     * ID of post with most likes.
     */
    private Long mostLikedPostId;

    /**
     * ID of post with most comments.
     */
    private Long mostCommentedPostId;

    /**
     * Distribution of posts by type.
     */
    private PostTypeDistribution postsByType;

    /**
     * Distribution of posts by visibility.
     */
    private PostVisibilityDistribution postsByVisibility;

    // =========================================================================
    // Nested DTOs
    // =========================================================================

    /**
     * Post type distribution.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PostTypeDistribution {
        private Long text;
        private Long image;
        private Long article;
        private Long video;
        private Long poll;
    }

    /**
     * Post visibility distribution.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PostVisibilityDistribution {
        private Long publicPosts;
        private Long connectionsOnly;
        private Long privatePosts;
    }
}

