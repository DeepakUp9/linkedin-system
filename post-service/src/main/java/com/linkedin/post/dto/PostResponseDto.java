package com.linkedin.post.dto;

import com.linkedin.post.model.PostType;
import com.linkedin.post.model.PostVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for returning post data to clients.
 * 
 * Purpose:
 * Response payload for GET /api/posts endpoints.
 * Contains all post data plus computed/enriched fields.
 * 
 * Design Pattern: DTO Pattern
 * - Hides internal entity structure
 * - Includes computed fields (hasLiked, author info)
 * - Enriched with user data from User Service
 * - Formatted for frontend consumption
 * 
 * API Response Example:
 * <pre>
 * GET /api/posts/123
 * 
 * {
 *   "id": 123,
 *   "author": {
 *     "id": 456,
 *     "displayName": "John Doe",
 *     "headline": "Software Engineer at Google",
 *     "profilePictureUrl": "https://..."
 *   },
 *   "type": "TEXT",
 *   "content": "Excited to share my new article! #Tech",
 *   "visibility": "PUBLIC",
 *   "imageUrls": [],
 *   "hashtags": ["Tech"],
 *   "mentions": [],
 *   "engagement": {
 *     "likesCount": 42,
 *     "commentsCount": 7,
 *     "sharesCount": 3,
 *     "viewsCount": 156
 *   },
 *   "hasLiked": false,
 *   "isAuthor": false,
 *   "canEdit": false,
 *   "canDelete": false,
 *   "sharedPost": null,
 *   "shareComment": null,
 *   "createdAt": "2024-01-15T10:30:00",
 *   "updatedAt": "2024-01-15T10:30:00"
 * }
 * </pre>
 * 
 * Enrichment Flow:
 * <pre>
 * Post Entity (from database)
 *   ↓
 * PostMapper.toDto() (MapStruct)
 *   ↓
 * Enrich with author data (Feign → User Service)
 *   ↓
 * Add computed fields (hasLiked, canEdit, canDelete)
 *   ↓
 * PostResponseDto (to frontend)
 * </pre>
 * 
 * @see com.linkedin.post.model.Post
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostResponseDto {

    /**
     * Post ID.
     */
    private Long id;

    /**
     * Author information (enriched from User Service).
     * 
     * Nested DTO containing:
     * - User ID
     * - Display name
     * - Headline
     * - Profile picture URL
     * 
     * Fetched via Feign client from User Service.
     */
    private AuthorDto author;

    /**
     * Type of post.
     */
    private PostType type;

    /**
     * Main text content.
     */
    private String content;

    /**
     * Visibility setting.
     */
    private PostVisibility visibility;

    /**
     * Image URLs (for IMAGE type posts).
     */
    private List<String> imageUrls;

    /**
     * Extracted hashtags (without # symbol).
     * 
     * Example: ["Tech", "Java", "SpringBoot"]
     */
    private List<String> hashtags;

    /**
     * Mentioned user IDs.
     * 
     * Example: [123, 456, 789]
     */
    private List<Long> mentions;

    /**
     * Engagement metrics (nested DTO).
     */
    private EngagementDto engagement;

    /**
     * Whether current user has liked this post.
     * 
     * Computed field based on current user's ID.
     * Queried from LikeRepository.
     */
    private Boolean hasLiked;

    /**
     * Whether current user is the author.
     * 
     * Computed field: currentUserId == post.authorId
     */
    private Boolean isAuthor;

    /**
     * Whether current user can edit this post.
     * 
     * Business logic: Only author can edit
     */
    private Boolean canEdit;

    /**
     * Whether current user can delete this post.
     * 
     * Business logic: Only author can delete
     */
    private Boolean canDelete;

    /**
     * Shared post data (if this is a repost).
     * 
     * Null for original posts.
     * Contains full PostResponseDto of original post for reposts.
     * 
     * Recursive structure:
     * PostResponseDto
     *   └─ sharedPost: PostResponseDto (original)
     *        └─ sharedPost: null (original doesn't have shared post)
     */
    private PostResponseDto sharedPost;

    /**
     * User's comment when sharing the post.
     * 
     * Only set if this is a shared post.
     */
    private String shareComment;

    /**
     * When post was created.
     */
    private LocalDateTime createdAt;

    /**
     * When post was last updated.
     */
    private LocalDateTime updatedAt;

    // =========================================================================
    // Nested DTOs
    // =========================================================================

    /**
     * Author information (from User Service).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AuthorDto {
        private Long id;
        private String displayName;
        private String headline;
        private String profilePictureUrl;
    }

    /**
     * Engagement metrics.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EngagementDto {
        private Integer likesCount;
        private Integer commentsCount;
        private Integer sharesCount;
        private Integer viewsCount;
        
        /**
         * Total engagement score.
         * 
         * Formula: likes + (comments × 2) + (shares × 3)
         */
        private Integer totalEngagement;
    }
}

