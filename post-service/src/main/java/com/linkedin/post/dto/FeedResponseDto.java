package com.linkedin.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for returning personalized feed data.
 * 
 * Purpose:
 * Response payload for GET /api/feed endpoint.
 * Contains paginated list of posts for user's home feed.
 * 
 * Design: Pagination + Metadata
 * - Contains list of posts
 * - Pagination info (page, size, total)
 * - Feed metadata (last updated, suggestions)
 * 
 * API Response Example:
 * <pre>
 * GET /api/feed?page=0&size=20
 * 
 * {
 *   "posts": [
 *     {
 *       "id": 123,
 *       "author": {...},
 *       "content": "Excited to announce...",
 *       ...
 *     },
 *     ...
 *   ],
 *   "pagination": {
 *     "page": 0,
 *     "size": 20,
 *     "totalElements": 156,
 *     "totalPages": 8,
 *     "hasNext": true,
 *     "hasPrevious": false
 *   },
 *   "feedMetadata": {
 *     "connectionsCount": 243,
 *     "postsInFeed": 156,
 *     "lastUpdated": "2024-01-15T13:00:00"
 *   }
 * }
 * </pre>
 * 
 * Feed Algorithm (Simplified):
 * 1. Get user's connections from Connection Service
 * 2. Get posts by connections (visibility check)
 * 3. Get user's own posts
 * 4. Merge and sort by recency
 * 5. Apply pagination
 * 6. Enrich with author data
 * 
 * Future Enhancements:
 * - Ranking algorithm (not just chronological)
 * - Personalized suggestions
 * - Sponsored content
 * - "You might like" section
 * 
 * @see com.linkedin.post.service.FeedService
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedResponseDto {

    /**
     * List of posts in the feed.
     */
    private List<PostResponseDto> posts;

    /**
     * Pagination metadata.
     */
    private PaginationDto pagination;

    /**
     * Feed-specific metadata.
     */
    private FeedMetadataDto feedMetadata;

    // =========================================================================
    // Nested DTOs
    // =========================================================================

    /**
     * Pagination information.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaginationDto {
        /**
         * Current page number (0-indexed).
         */
        private Integer page;

        /**
         * Number of items per page.
         */
        private Integer size;

        /**
         * Total number of items across all pages.
         */
        private Long totalElements;

        /**
         * Total number of pages.
         */
        private Integer totalPages;

        /**
         * Whether there's a next page.
         */
        private Boolean hasNext;

        /**
         * Whether there's a previous page.
         */
        private Boolean hasPrevious;
    }

    /**
     * Feed metadata.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FeedMetadataDto {
        /**
         * Number of user's connections.
         */
        private Integer connectionsCount;

        /**
         * Total posts available in feed.
         */
        private Long postsInFeed;

        /**
         * When feed was last generated.
         */
        private String lastUpdated;
    }
}

