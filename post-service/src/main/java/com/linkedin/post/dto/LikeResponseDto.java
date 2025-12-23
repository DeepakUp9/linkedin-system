package com.linkedin.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for returning like information.
 * 
 * Purpose:
 * Response payload for "who liked this" queries.
 * Shows which users liked a post or comment.
 * 
 * API Response Example:
 * <pre>
 * GET /api/posts/123/likes
 * 
 * {
 *   "content": [
 *     {
 *       "id": 1,
 *       "user": {
 *         "id": 456,
 *         "displayName": "John Doe",
 *         "headline": "Software Engineer",
 *         "profilePictureUrl": "https://..."
 *       },
 *       "postId": 123,
 *       "commentId": null,
 *       "isPostLike": true,
 *       "isCommentLike": false,
 *       "createdAt": "2024-01-15T12:00:00"
 *     },
 *     ...
 *   ],
 *   "totalElements": 42,
 *   "page": 0,
 *   "size": 20
 * }
 * </pre>
 * 
 * Use Cases:
 * - "Liked by Sarah, John, and 40 others"
 * - Social proof ("3 of your connections liked this")
 * - User activity page ("Posts you've liked")
 * 
 * @see com.linkedin.post.model.Like
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LikeResponseDto {

    /**
     * Like record ID.
     */
    private Long id;

    /**
     * User who liked (from User Service).
     */
    private UserDto user;

    /**
     * ID of post that was liked.
     */
    private Long postId;

    /**
     * ID of comment that was liked (null for post likes).
     */
    private Long commentId;

    /**
     * Whether this is a post like.
     * 
     * Computed: commentId == null
     */
    private Boolean isPostLike;

    /**
     * Whether this is a comment like.
     * 
     * Computed: commentId != null
     */
    private Boolean isCommentLike;

    /**
     * When the like was created.
     */
    private LocalDateTime createdAt;

    // =========================================================================
    // Nested DTO
    // =========================================================================

    /**
     * User information (from User Service).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserDto {
        private Long id;
        private String displayName;
        private String headline;
        private String profilePictureUrl;
        
        /**
         * Whether this user is a connection of the current user.
         * 
         * Used for social proof:
         * "Liked by Sarah Johnson (your connection) and 39 others"
         */
        private Boolean isConnection;
    }
}

