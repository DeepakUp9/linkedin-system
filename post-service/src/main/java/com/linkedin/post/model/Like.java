package com.linkedin.post.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA Entity representing a "like" on a post or comment.
 * 
 * Purpose:
 * Tracks which users have liked which posts/comments.
 * Simple many-to-many relationship: User ↔ Post/Comment
 * 
 * Features:
 * - Like posts
 * - Like comments
 * - One like per user per post/comment (enforced by unique constraint)
 * - Timestamp of when like was created
 * 
 * Business Rules:
 * 1. User cannot like their own post
 * 2. User can only like a post/comment once
 * 3. User can unlike (delete like record)
 * 4. Liking increments post/comment likes_count
 * 5. Unliking decrements post/comment likes_count
 * 
 * Database Design:
 * - Unique constraint: (user_id, post_id, comment_id)
 * - Ensures one like per user per target
 * - Either post_id OR comment_id is set (not both)
 * 
 * Example Usage:
 * <pre>
 * {@code
 * // Like a post
 * Like like = Like.builder()
 *     .userId(123L)
 *     .postId(456L)
 *     .commentId(null)
 *     .build();
 * likeRepository.save(like);
 * 
 * // Like a comment
 * Like commentLike = Like.builder()
 *     .userId(123L)
 *     .postId(456L)      // Post where comment is
 *     .commentId(789L)   // Comment being liked
 *     .build();
 * likeRepository.save(commentLike);
 * 
 * // Unlike (delete record)
 * likeRepository.delete(like);
 * }
 * </pre>
 * 
 * Performance Considerations:
 * - Indexed on userId for "posts liked by user" queries
 * - Indexed on postId for "likes on post" queries
 * - Indexed on commentId for "likes on comment" queries
 * - Composite index for quick duplicate check
 * 
 * @see Post
 * @see Comment
 */
@Entity
@Table(name = "likes", 
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_likes_user_post_comment",
            columnNames = {"user_id", "post_id", "comment_id"}
        )
    },
    indexes = {
        @Index(name = "idx_likes_user", columnList = "user_id"),
        @Index(name = "idx_likes_post", columnList = "post_id"),
        @Index(name = "idx_likes_comment", columnList = "comment_id"),
        @Index(name = "idx_likes_created", columnList = "created_at")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID of the user who liked.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * ID of the post that was liked.
     * Always set (even for comment likes, to know which post the comment is on).
     */
    @Column(name = "post_id", nullable = false)
    private Long postId;

    /**
     * ID of the comment that was liked (if liking a comment).
     * NULL if liking a post directly.
     */
    @Column(name = "comment_id")
    private Long commentId;

    /**
     * When the like was created (auto-populated).
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // =========================================================================
    // Business Logic Methods
    // =========================================================================

    /**
     * Check if this is a post like (not a comment like).
     * 
     * @return true if liking a post directly
     */
    public boolean isPostLike() {
        return commentId == null;
    }

    /**
     * Check if this is a comment like.
     * 
     * @return true if liking a comment
     */
    public boolean isCommentLike() {
        return commentId != null;
    }

    /**
     * Check if this like is from a specific user.
     * 
     * @param checkUserId User ID to check
     * @return true if this like is from the specified user
     */
    public boolean isFromUser(Long checkUserId) {
        return userId.equals(checkUserId);
    }

    /**
     * Check if this like is on a specific post.
     * 
     * @param checkPostId Post ID to check
     * @return true if this like is on the specified post
     */
    public boolean isOnPost(Long checkPostId) {
        return postId.equals(checkPostId) && commentId == null;
    }

    /**
     * Check if this like is on a specific comment.
     * 
     * @param checkCommentId Comment ID to check
     * @return true if this like is on the specified comment
     */
    public boolean isOnComment(Long checkCommentId) {
        return commentId != null && commentId.equals(checkCommentId);
    }
}

