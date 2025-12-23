package com.linkedin.post.repository;

import com.linkedin.post.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for {@link Comment} entities.
 * 
 * Purpose:
 * Provides database access for comments with support for:
 * - Root-level comments (parent_comment_id = NULL)
 * - Nested replies (parent_comment_id = comment.id)
 * - Comment threading and tree structure
 * 
 * Design Pattern: Composite Pattern
 * - Comments can contain other comments (replies)
 * - Tree structure: Root → Reply → Reply to Reply
 * - Queries support both levels and nested structure
 * 
 * Nested Comment Structure:
 * <pre>
 * Post (id=1)
 *   ├─ Comment A (id=10, parent=NULL) ← Root
 *   │    ├─ Comment B (id=11, parent=10) ← Reply
 *   │    └─ Comment C (id=12, parent=10) ← Reply
 *   │         └─ Comment D (id=13, parent=12) ← Nested reply
 *   └─ Comment E (id=14, parent=NULL) ← Root
 * </pre>
 * 
 * Query Patterns:
 * 
 * 1. Get Root Comments:
 * <pre>
 * findRootCommentsByPostId(postId, pageable)
 *   ↓
 * SELECT * FROM comments 
 * WHERE post_id = ? AND parent_comment_id IS NULL 
 * AND is_deleted = FALSE
 * ORDER BY created_at
 * </pre>
 * 
 * 2. Get Replies to Comment:
 * <pre>
 * findRepliesByParentId(commentId, pageable)
 *   ↓
 * SELECT * FROM comments 
 * WHERE parent_comment_id = ?
 * AND is_deleted = FALSE
 * ORDER BY created_at
 * </pre>
 * 
 * Usage Example:
 * <pre>
 * {@code
 * // Get root comments on a post
 * Page<Comment> rootComments = commentRepository.findRootCommentsByPostId(
 *     postId, 
 *     PageRequest.of(0, 20)
 * );
 * 
 * // For each root comment, get replies
 * for (Comment root : rootComments) {
 *     List<Comment> replies = commentRepository.findRepliesByParentId(
 *         root.getId(),
 *         PageRequest.of(0, 5)
 *     );
 *     // Display replies...
 * }
 * }
 * </pre>
 * 
 * @see Comment
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // =========================================================================
    // Root Comments (parent_comment_id = NULL)
    // =========================================================================
    
    /**
     * Find root-level comments on a post (not replies).
     * 
     * Root comments are comments directly on the post, not replies to other comments.
     * 
     * Spring Data generates:
     * SELECT * FROM comments 
     * WHERE post_id = ? 
     * AND parent_comment_id IS NULL 
     * AND is_deleted = FALSE
     * ORDER BY created_at
     * 
     * Uses Index: idx_comments_post_root
     * 
     * @param postId Post ID
     * @param pageable Pagination parameters
     * @return Page of root comments
     */
    Page<Comment> findByPostIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAt(
            Long postId, 
            Pageable pageable
    );
    
    /**
     * Count root comments on a post.
     * 
     * Use Case: "Show 25 comments" label
     * 
     * @param postId Post ID
     * @return Count of root comments
     */
    long countByPostIdAndParentCommentIdIsNullAndDeletedFalse(Long postId);

    // =========================================================================
    // Nested Replies (parent_comment_id = comment.id)
    // =========================================================================
    
    /**
     * Find replies to a specific comment.
     * 
     * Replies are comments with parent_comment_id set to another comment's ID.
     * 
     * Spring Data generates:
     * SELECT * FROM comments 
     * WHERE parent_comment_id = ? 
     * AND is_deleted = FALSE
     * ORDER BY created_at
     * 
     * Uses Index: idx_comments_parent
     * 
     * @param parentCommentId Parent comment ID
     * @param pageable Pagination parameters
     * @return Page of replies
     */
    Page<Comment> findByParentCommentIdAndDeletedFalseOrderByCreatedAt(
            Long parentCommentId, 
            Pageable pageable
    );
    
    /**
     * Count replies to a specific comment.
     * 
     * Use Case: "Show 3 replies" button
     * 
     * @param parentCommentId Parent comment ID
     * @return Count of replies
     */
    long countByParentCommentIdAndDeletedFalse(Long parentCommentId);

    // =========================================================================
    // All Comments on Post (Root + Replies)
    // =========================================================================
    
    /**
     * Find ALL comments on a post (both root and replies).
     * 
     * Use Case: Admin view, full comment export
     * 
     * @param postId Post ID
     * @param pageable Pagination parameters
     * @return Page of all comments
     */
    Page<Comment> findByPostIdAndDeletedFalseOrderByCreatedAt(
            Long postId,
            Pageable pageable
    );
    
    /**
     * Count ALL comments on a post (root + replies).
     * 
     * Use Case: Total comment count display
     * 
     * @param postId Post ID
     * @return Total count of comments
     */
    long countByPostIdAndDeletedFalse(Long postId);

    // =========================================================================
    // User's Comments
    // =========================================================================
    
    /**
     * Find all comments by a specific author.
     * 
     * Use Case: User activity page, "Comments by this user"
     * 
     * @param authorId Author's user ID
     * @param pageable Pagination parameters
     * @return Page of comments by user
     */
    Page<Comment> findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(
            Long authorId,
            Pageable pageable
    );
    
    /**
     * Count comments by author.
     * 
     * Use Case: Profile statistics
     * 
     * @param authorId Author's user ID
     * @return Count of comments
     */
    long countByAuthorIdAndDeletedFalse(Long authorId);

    // =========================================================================
    // Single Comment Retrieval
    // =========================================================================
    
    /**
     * Find a comment by ID, excluding deleted.
     * 
     * Use Case: Load specific comment for editing/viewing
     * 
     * @param id Comment ID
     * @return Optional containing comment if found
     */
    Optional<Comment> findByIdAndDeletedFalse(Long id);
    
    /**
     * Check if a comment exists and is not deleted.
     * 
     * Use Case: Quick validation
     * 
     * @param id Comment ID
     * @return true if exists and not deleted
     */
    boolean existsByIdAndDeletedFalse(Long id);

    // =========================================================================
    // Custom Queries for Complex Operations
    // =========================================================================
    
    /**
     * Find most liked comments on a post.
     * 
     * Use Case: "Top comments" sorting option
     * 
     * @param postId Post ID
     * @param pageable Pagination parameters
     * @return List of comments sorted by likes
     */
    @Query("""
        SELECT c FROM Comment c 
        WHERE c.postId = :postId 
        AND c.deleted = false
        AND c.parentCommentId IS NULL
        ORDER BY c.likesCount DESC, c.createdAt DESC
        """)
    List<Comment> findTopCommentsByLikes(
            @Param("postId") Long postId,
            Pageable pageable
    );
    
    /**
     * Find comments with most replies (popular discussions).
     * 
     * Use Case: "Most discussed" comments
     * 
     * @param postId Post ID
     * @param pageable Pagination parameters
     * @return List of comments sorted by reply count
     */
    @Query("""
        SELECT c FROM Comment c 
        WHERE c.postId = :postId 
        AND c.deleted = false
        AND c.parentCommentId IS NULL
        ORDER BY c.repliesCount DESC, c.createdAt DESC
        """)
    List<Comment> findCommentsByRepliesCount(
            @Param("postId") Long postId,
            Pageable pageable
    );
    
    /**
     * Find recent comments by a user on a specific post.
     * 
     * Use Case: Check if user already commented on this post
     * 
     * @param postId Post ID
     * @param authorId Author ID
     * @param pageable Pagination parameters
     * @return List of user's comments on the post
     */
    List<Comment> findByPostIdAndAuthorIdAndDeletedFalseOrderByCreatedAtDesc(
            Long postId,
            Long authorId,
            Pageable pageable
    );

    // =========================================================================
    // Update Queries (Engagement Counters)
    // =========================================================================
    
    /**
     * Increment likes count for a comment.
     * 
     * Called when: User likes a comment
     * 
     * @param commentId Comment ID
     */
    @Modifying
    @Query("UPDATE Comment c SET c.likesCount = c.likesCount + 1 WHERE c.id = :commentId")
    void incrementLikesCount(@Param("commentId") Long commentId);
    
    /**
     * Decrement likes count for a comment.
     * 
     * Called when: User unlikes a comment
     * 
     * @param commentId Comment ID
     */
    @Modifying
    @Query("UPDATE Comment c SET c.likesCount = CASE WHEN c.likesCount > 0 THEN c.likesCount - 1 ELSE 0 END WHERE c.id = :commentId")
    void decrementLikesCount(@Param("commentId") Long commentId);
    
    /**
     * Increment replies count for a comment.
     * 
     * Called when: Someone replies to this comment
     * 
     * @param commentId Parent comment ID
     */
    @Modifying
    @Query("UPDATE Comment c SET c.repliesCount = c.repliesCount + 1 WHERE c.id = :commentId")
    void incrementRepliesCount(@Param("commentId") Long commentId);
    
    /**
     * Decrement replies count for a comment.
     * 
     * Called when: A reply is deleted
     * 
     * @param commentId Parent comment ID
     */
    @Modifying
    @Query("UPDATE Comment c SET c.repliesCount = CASE WHEN c.repliesCount > 0 THEN c.repliesCount - 1 ELSE 0 END WHERE c.id = :commentId")
    void decrementRepliesCount(@Param("commentId") Long commentId);

    // =========================================================================
    // Batch Operations
    // =========================================================================
    
    /**
     * Find all comments on a post (for batch operations like post deletion).
     * 
     * Use Case: When deleting a post, also soft-delete all comments
     * 
     * @param postId Post ID
     * @return List of all comments on the post
     */
    List<Comment> findByPostId(Long postId);
    
    /**
     * Find all replies to a comment (for cascade deletion).
     * 
     * Use Case: When deleting a comment, also delete all its replies
     * 
     * @param parentCommentId Parent comment ID
     * @return List of all replies
     */
    List<Comment> findByParentCommentId(Long parentCommentId);
}

