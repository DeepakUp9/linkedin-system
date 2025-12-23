package com.linkedin.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for returning comment data to clients.
 * 
 * Purpose:
 * Response payload for comment-related endpoints.
 * Supports nested reply structure.
 * 
 * Design Pattern: Composite Pattern
 * - Can contain child comments (replies)
 * - Recursive structure for nested threads
 * - Tree representation of comment discussions
 * 
 * API Response Example (with nested replies):
 * <pre>
 * GET /api/posts/123/comments
 * 
 * {
 *   "id": 456,
 *   "postId": 123,
 *   "author": {
 *     "id": 789,
 *     "displayName": "Sarah Johnson",
 *     "headline": "Product Manager at Microsoft",
 *     "profilePictureUrl": "https://..."
 *   },
 *   "content": "Great article! Could you elaborate on point 2?",
 *   "parentCommentId": null,
 *   "isRootComment": true,
 *   "likesCount": 5,
 *   "repliesCount": 2,
 *   "hasLiked": false,
 *   "isAuthor": false,
 *   "canEdit": false,
 *   "canDelete": false,
 *   "isEdited": false,
 *   "editedAt": null,
 *   "replies": [
 *     {
 *       "id": 457,
 *       "content": "Sure! Point 2 refers to...",
 *       "parentCommentId": 456,
 *       "isRootComment": false,
 *       "likesCount": 2,
 *       "repliesCount": 0,
 *       "replies": [],
 *       ...
 *     }
 *   ],
 *   "createdAt": "2024-01-15T11:00:00",
 *   "updatedAt": "2024-01-15T11:00:00"
 * }
 * </pre>
 * 
 * Loading Strategy:
 * 
 * Option 1: Load all levels (memory intensive):
 * <pre>
 * Comment A
 *   ├─ Reply B
 *   │   └─ Reply D
 *   └─ Reply C
 * </pre>
 * 
 * Option 2: Load on-demand (better UX):
 * <pre>
 * Comment A
 *   ├─ "Show 2 replies" ← Click to load
 * </pre>
 * 
 * We use Option 2: replies[] is empty by default,
 * loaded separately when user clicks "Show replies".
 * 
 * @see com.linkedin.post.model.Comment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentResponseDto {

    /**
     * Comment ID.
     */
    private Long id;

    /**
     * ID of post this comment belongs to.
     */
    private Long postId;

    /**
     * Author information (from User Service).
     */
    private AuthorDto author;

    /**
     * Comment text content.
     */
    private String content;

    /**
     * ID of parent comment (null for root comments).
     */
    private Long parentCommentId;

    /**
     * Whether this is a root-level comment.
     * 
     * Computed field: parentCommentId == null
     */
    private Boolean isRootComment;

    /**
     * Number of likes on this comment.
     */
    private Integer likesCount;

    /**
     * Number of replies to this comment.
     */
    private Integer repliesCount;

    /**
     * Whether current user has liked this comment.
     * 
     * Computed field based on current user's ID.
     */
    private Boolean hasLiked;

    /**
     * Whether current user is the comment author.
     */
    private Boolean isAuthor;

    /**
     * Whether current user can edit this comment.
     * 
     * Business logic: Only author can edit
     */
    private Boolean canEdit;

    /**
     * Whether current user can delete this comment.
     * 
     * Business logic:
     * - Author can delete their own comment
     * - Post author can delete any comment on their post
     */
    private Boolean canDelete;

    /**
     * Whether comment has been edited.
     */
    private Boolean isEdited;

    /**
     * When comment was edited (if edited).
     */
    private LocalDateTime editedAt;

    /**
     * Nested replies (optional).
     * 
     * Loaded separately when user requests.
     * Empty list by default to avoid deep nesting.
     * 
     * Use Case:
     * - User clicks "Show 3 replies"
     * - Frontend makes request: GET /api/comments/{id}/replies
     * - Populate this field with replies
     */
    @Builder.Default
    private List<CommentResponseDto> replies = List.of();

    /**
     * When comment was created.
     */
    private LocalDateTime createdAt;

    /**
     * When comment was last updated.
     */
    private LocalDateTime updatedAt;

    // =========================================================================
    // Nested DTO
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
}

