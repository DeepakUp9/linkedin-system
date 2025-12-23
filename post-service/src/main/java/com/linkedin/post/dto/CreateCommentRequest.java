package com.linkedin.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a comment on a post or replying to a comment.
 * 
 * Purpose:
 * Request payload for commenting on posts or replying to comments.
 * Supports nested comment structure via parentCommentId.
 * 
 * Design Pattern: Composite Pattern (in Comment entity)
 * - parentCommentId = null → Root comment on post
 * - parentCommentId = X → Reply to comment X
 * 
 * API Request Examples:
 * 
 * 1. Root Comment on Post:
 * <pre>
 * POST /api/posts/{postId}/comments
 * {
 *   "content": "Great post! Very insightful.",
 *   "parentCommentId": null
 * }
 * </pre>
 * 
 * 2. Reply to Comment:
 * <pre>
 * POST /api/posts/{postId}/comments
 * {
 *   "content": "I agree with your point!",
 *   "parentCommentId": 123
 * }
 * </pre>
 * 
 * Flow:
 * <pre>
 * User clicks "Comment" on post
 *   ↓
 * Frontend sends CreateCommentRequest (parentCommentId = null)
 *   ↓
 * Service creates root comment
 *   ↓
 * Post.commentsCount++
 *   ↓
 * Publish CommentCreatedEvent to Kafka
 *   ↓
 * Notification Service notifies post author
 * </pre>
 * 
 * Validation Rules:
 * - content: Required, 1-1000 characters
 * - parentCommentId: Optional (null for root comments)
 * 
 * @see com.linkedin.post.controller.PostController#addComment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCommentRequest {

    /**
     * Text content of the comment.
     * 
     * Constraints:
     * - Cannot be blank
     * - Must be 1-1000 characters (shorter than posts)
     * 
     * Examples:
     * - "Great insights!"
     * - "Could you elaborate on the second point?"
     * - "Thanks for sharing this!"
     */
    @NotBlank(message = "Comment content cannot be empty")
    @Size(min = 1, max = 1000, message = "Comment must be between 1 and 1000 characters")
    private String content;

    /**
     * ID of parent comment if this is a reply.
     * 
     * Null = Root comment (directly on post)
     * Non-null = Reply to comment with this ID
     * 
     * Business Rules:
     * - Can reply to root comments
     * - Can reply to replies (nested threading)
     * - Cannot reply to deleted comments
     * - parentCommentId must exist and be on same post
     * 
     * Example Nested Structure:
     * <pre>
     * Comment A (parentId = null) ← Root
     *   ├─ Comment B (parentId = A.id) ← Reply
     *   └─ Comment C (parentId = A.id) ← Reply
     *        └─ Comment D (parentId = C.id) ← Nested reply
     * </pre>
     */
    private Long parentCommentId;
}

