package com.linkedin.post.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * JPA Entity representing a comment on a post.
 * 
 * Purpose:
 * Stores user comments on posts with support for nested replies.
 * 
 * Features:
 * - Comments on posts
 * - Replies to comments (nested structure)
 * - Like support (likes on comments)
 * - Soft delete
 * - Edit history tracking
 * 
 * Nested Structure:
 * <pre>
 * Post
 *   └─ Comment 1 (parentId = null)
 *        ├─ Reply 1.1 (parentId = Comment1.id)
 *        └─ Reply 1.2 (parentId = Comment1.id)
 *             └─ Reply 1.2.1 (parentId = Reply1.2.id)
 *   └─ Comment 2 (parentId = null)
 * </pre>
 * 
 * Design Pattern: Composite Pattern
 * - Comments can have child comments (replies)
 * - Tree structure for nested conversations
 * - Recursive queries to fetch full thread
 * 
 * Business Rules:
 * 1. Cannot comment on deleted posts
 * 2. Cannot reply to deleted comments
 * 3. Author can edit their own comments
 * 4. Author can delete their own comments
 * 5. Post author can delete any comment on their post
 * 
 * Example Usage:
 * <pre>
 * {@code
 * // Root comment
 * Comment comment = Comment.builder()
 *     .postId(456L)
 *     .authorId(123L)
 *     .content("Great post!")
 *     .parentCommentId(null)  // Root comment
 *     .build();
 * 
 * // Reply to comment
 * Comment reply = Comment.builder()
 *     .postId(456L)
 *     .authorId(789L)
 *     .content("I agree!")
 *     .parentCommentId(comment.getId())  // Reply
 *     .build();
 * }
 * </pre>
 * 
 * @see Post
 */
@Entity
@Table(name = "comments", indexes = {
    @Index(name = "idx_comments_post", columnList = "post_id"),
    @Index(name = "idx_comments_author", columnList = "author_id"),
    @Index(name = "idx_comments_parent", columnList = "parent_comment_id"),
    @Index(name = "idx_comments_created", columnList = "created_at"),
    @Index(name = "idx_comments_deleted", columnList = "is_deleted"),
    @Index(name = "idx_comments_post_parent", columnList = "post_id, parent_comment_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID of the post this comment belongs to.
     */
    @Column(name = "post_id", nullable = false)
    private Long postId;

    /**
     * ID of the user who wrote this comment.
     */
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /**
     * Text content of the comment.
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * ID of the parent comment (for nested replies).
     * NULL for root-level comments.
     * Set to comment ID for replies.
     */
    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    /**
     * Number of likes on this comment.
     */
    @Column(name = "likes_count", nullable = false)
    @Builder.Default
    private Integer likesCount = 0;

    /**
     * Number of replies to this comment.
     */
    @Column(name = "replies_count", nullable = false)
    @Builder.Default
    private Integer repliesCount = 0;

    /**
     * Whether this comment has been edited.
     */
    @Column(name = "is_edited", nullable = false)
    @Builder.Default
    private boolean edited = false;

    /**
     * When the comment was last edited.
     */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    /**
     * Whether this comment has been deleted (soft delete).
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /**
     * When the comment was deleted.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * When the comment was created (auto-populated).
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When the comment was last updated (auto-populated).
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // =========================================================================
    // Business Logic Methods
    // =========================================================================

    /**
     * Check if this is a root-level comment (not a reply).
     * 
     * @return true if this is a root comment
     */
    public boolean isRootComment() {
        return parentCommentId == null;
    }

    /**
     * Check if this is a reply to another comment.
     * 
     * @return true if this is a reply
     */
    public boolean isReply() {
        return parentCommentId != null;
    }

    /**
     * Check if a user can edit this comment.
     * Only author can edit, and comment must not be deleted.
     * 
     * @param userId ID of the user trying to edit
     * @return true if user can edit
     */
    public boolean canBeEditedBy(Long userId) {
        return !deleted && userId.equals(authorId);
    }

    /**
     * Check if a user can delete this comment.
     * Author or post author can delete.
     * 
     * @param userId ID of the user trying to delete
     * @param postAuthorId ID of the post author
     * @return true if user can delete
     */
    public boolean canBeDeletedBy(Long userId, Long postAuthorId) {
        if (deleted) {
            return false;
        }
        // Author or post owner can delete
        return userId.equals(authorId) || userId.equals(postAuthorId);
    }

    /**
     * Mark comment as edited.
     */
    public void markAsEdited() {
        this.edited = true;
        this.editedAt = LocalDateTime.now();
    }

    /**
     * Soft delete this comment.
     */
    public void markAsDeleted() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        // Replace content with placeholder for deleted comments
        this.content = "[This comment has been deleted]";
    }

    /**
     * Increment likes count.
     */
    public void incrementLikes() {
        this.likesCount++;
    }

    /**
     * Decrement likes count (cannot go below 0).
     */
    public void decrementLikes() {
        if (this.likesCount > 0) {
            this.likesCount--;
        }
    }

    /**
     * Increment replies count.
     */
    public void incrementReplies() {
        this.repliesCount++;
    }

    /**
     * Decrement replies count (cannot go below 0).
     */
    public void decrementReplies() {
        if (this.repliesCount > 0) {
            this.repliesCount--;
        }
    }

    /**
     * Check if comment has replies.
     * 
     * @return true if replies count > 0
     */
    public boolean hasReplies() {
        return repliesCount > 0;
    }

    /**
     * Get depth level (0 for root, 1 for first-level reply, etc.)
     * Note: This requires querying parent comments recursively.
     * 
     * For now, returns 0 for root, 1 for any reply.
     * 
     * @return Depth level
     */
    public int getDepthLevel() {
        return isRootComment() ? 0 : 1;
    }
}

