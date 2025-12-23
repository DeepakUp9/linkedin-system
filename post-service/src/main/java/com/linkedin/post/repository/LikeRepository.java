package com.linkedin.post.repository;

import com.linkedin.post.model.Like;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for {@link Like} entities.
 * 
 * Purpose:
 * Provides database access for likes on posts and comments.
 * Simple entity with powerful queries for engagement tracking.
 * 
 * Like System Logic:
 * 
 * 1. Like a Post:
 * <pre>
 * Like like = Like.builder()
 *     .userId(123L)
 *     .postId(456L)
 *     .commentId(null)  ← NULL for post likes
 *     .build();
 * likeRepository.save(like);
 * </pre>
 * 
 * 2. Like a Comment:
 * <pre>
 * Like like = Like.builder()
 *     .userId(123L)
 *     .postId(456L)      ← Post where comment is
 *     .commentId(789L)   ← Comment being liked
 *     .build();
 * likeRepository.save(like);
 * </pre>
 * 
 * 3. Unlike (Delete):
 * <pre>
 * likeRepository.deleteByUserIdAndPostIdAndCommentId(123L, 456L, null);
 * </pre>
 * 
 * 4. Check if Liked:
 * <pre>
 * boolean liked = likeRepository.existsByUserIdAndPostIdAndCommentIdIsNull(123L, 456L);
 * </pre>
 * 
 * Database Constraints:
 * - UNIQUE(user_id, post_id, comment_id)
 * - Prevents duplicate likes
 * - One like per user per target
 * 
 * Usage Example:
 * <pre>
 * {@code
 * // Check if user liked a post
 * boolean hasLiked = likeRepository.existsByUserIdAndPostIdAndCommentIdIsNull(
 *     userId, 
 *     postId
 * );
 * 
 * if (hasLiked) {
 *     // Unlike
 *     likeRepository.deleteByUserIdAndPostIdAndCommentIdIsNull(userId, postId);
 * } else {
 *     // Like
 *     Like like = new Like(null, userId, postId, null, null);
 *     likeRepository.save(like);
 * }
 * 
 * // Get posts liked by user
 * List<Long> likedPostIds = likeRepository.findPostIdsLikedByUser(userId);
 * }
 * </pre>
 * 
 * @see Like
 */
@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {

    // =========================================================================
    // Post Like Queries
    // =========================================================================
    
    /**
     * Check if a user has liked a specific post.
     * 
     * Uses: Unique constraint on (user_id, post_id, comment_id)
     * Fast boolean check via index: idx_likes_user_post
     * 
     * @param userId User ID
     * @param postId Post ID
     * @return true if user liked the post
     */
    boolean existsByUserIdAndPostIdAndCommentIdIsNull(Long userId, Long postId);
    
    /**
     * Find the like record for a user on a post.
     * 
     * Use Case: Get like to delete it (unlike)
     * 
     * @param userId User ID
     * @param postId Post ID
     * @return Optional containing like if found
     */
    Optional<Like> findByUserIdAndPostIdAndCommentIdIsNull(Long userId, Long postId);
    
    /**
     * Delete a user's like on a post (unlike).
     * 
     * Spring Data generates:
     * DELETE FROM likes 
     * WHERE user_id = ? 
     * AND post_id = ? 
     * AND comment_id IS NULL
     * 
     * @param userId User ID
     * @param postId Post ID
     * @return Number of records deleted (0 or 1)
     */
    int deleteByUserIdAndPostIdAndCommentIdIsNull(Long userId, Long postId);
    
    /**
     * Count likes on a post.
     * 
     * Use Case: Display like count (alternative to denormalized count)
     * 
     * @param postId Post ID
     * @return Number of likes
     */
    long countByPostIdAndCommentIdIsNull(Long postId);
    
    /**
     * Find all users who liked a post.
     * 
     * Use Case: "Liked by John, Sarah, and 23 others"
     * 
     * @param postId Post ID
     * @param pageable Pagination parameters
     * @return Page of likes
     */
    Page<Like> findByPostIdAndCommentIdIsNullOrderByCreatedAtDesc(
            Long postId, 
            Pageable pageable
    );

    // =========================================================================
    // Comment Like Queries
    // =========================================================================
    
    /**
     * Check if a user has liked a specific comment.
     * 
     * Similar to post likes, but comment_id is set.
     * 
     * @param userId User ID
     * @param commentId Comment ID
     * @return true if user liked the comment
     */
    boolean existsByUserIdAndCommentId(Long userId, Long commentId);
    
    /**
     * Find the like record for a user on a comment.
     * 
     * Use Case: Get like to delete it (unlike comment)
     * 
     * @param userId User ID
     * @param postId Post ID (where comment is)
     * @param commentId Comment ID
     * @return Optional containing like if found
     */
    Optional<Like> findByUserIdAndPostIdAndCommentId(
            Long userId, 
            Long postId, 
            Long commentId
    );
    
    /**
     * Delete a user's like on a comment (unlike).
     * 
     * @param userId User ID
     * @param postId Post ID
     * @param commentId Comment ID
     * @return Number of records deleted (0 or 1)
     */
    int deleteByUserIdAndPostIdAndCommentId(
            Long userId, 
            Long postId, 
            Long commentId
    );
    
    /**
     * Count likes on a comment.
     * 
     * Use Case: Display like count on comment
     * 
     * @param commentId Comment ID
     * @return Number of likes
     */
    long countByCommentId(Long commentId);
    
    /**
     * Find all users who liked a comment.
     * 
     * Use Case: Show who liked a comment
     * 
     * @param commentId Comment ID
     * @param pageable Pagination parameters
     * @return Page of likes
     */
    Page<Like> findByCommentIdOrderByCreatedAtDesc(
            Long commentId, 
            Pageable pageable
    );

    // =========================================================================
    // User Activity Queries
    // =========================================================================
    
    /**
     * Find all posts liked by a user.
     * 
     * Use Case: "Posts you've liked" page
     * 
     * @param userId User ID
     * @param pageable Pagination parameters
     * @return Page of likes (with post info)
     */
    Page<Like> findByUserIdAndCommentIdIsNullOrderByCreatedAtDesc(
            Long userId, 
            Pageable pageable
    );
    
    /**
     * Count posts liked by a user.
     * 
     * Use Case: Profile statistics
     * 
     * @param userId User ID
     * @return Number of posts liked
     */
    long countByUserIdAndCommentIdIsNull(Long userId);
    
    /**
     * Get post IDs liked by a user (for feed personalization).
     * 
     * Use Case: "Don't show posts I already liked"
     * 
     * @param userId User ID
     * @return List of post IDs
     */
    @Query("SELECT l.postId FROM Like l WHERE l.userId = :userId AND l.commentId IS NULL")
    List<Long> findPostIdsLikedByUser(@Param("userId") Long userId);

    // =========================================================================
    // Batch Queries (for Deletion Cascade)
    // =========================================================================
    
    /**
     * Find all likes on a post (both post likes and comment likes).
     * 
     * Use Case: When deleting a post, also delete all related likes
     * 
     * @param postId Post ID
     * @return List of all likes
     */
    List<Like> findByPostId(Long postId);
    
    /**
     * Delete all likes on a post.
     * 
     * Use Case: Cascade deletion when post is permanently deleted
     * 
     * Note: Normally handled by DB CASCADE, this is for manual cleanup
     * 
     * @param postId Post ID
     * @return Number of likes deleted
     */
    int deleteByPostId(Long postId);
    
    /**
     * Find all likes on a comment.
     * 
     * Use Case: When deleting a comment, also delete all likes on it
     * 
     * @param commentId Comment ID
     * @return List of likes
     */
    List<Like> findByCommentId(Long commentId);
    
    /**
     * Delete all likes on a comment.
     * 
     * Use Case: Cascade deletion when comment is permanently deleted
     * 
     * @param commentId Comment ID
     * @return Number of likes deleted
     */
    int deleteByCommentId(Long commentId);

    // =========================================================================
    // Analytics Queries
    // =========================================================================
    
    /**
     * Find most liked posts in a time period.
     * 
     * Use Case: "Most liked posts this week" analytics
     * 
     * @param pageable Pagination parameters
     * @return List of post IDs with like counts
     */
    @Query("""
        SELECT l.postId, COUNT(l.id) as likeCount
        FROM Like l 
        WHERE l.commentId IS NULL
        GROUP BY l.postId 
        ORDER BY COUNT(l.id) DESC
        """)
    List<Object[]> findMostLikedPosts(Pageable pageable);
    
    /**
     * Find users who liked a specific post (just user IDs).
     * 
     * Use Case: Check if any of user's connections liked this post
     * 
     * @param postId Post ID
     * @return List of user IDs
     */
    @Query("SELECT l.userId FROM Like l WHERE l.postId = :postId AND l.commentId IS NULL")
    List<Long> findUserIdsWhoLikedPost(@Param("postId") Long postId);
    
    /**
     * Check if any of the user's connections liked a post.
     * 
     * Use Case: "Liked by John (your connection)" social proof
     * 
     * @param postId Post ID
     * @param connectionIds List of user's connection IDs
     * @return true if any connection liked the post
     */
    @Query("""
        SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END
        FROM Like l 
        WHERE l.postId = :postId 
        AND l.commentId IS NULL
        AND l.userId IN :connectionIds
        """)
    boolean isLikedByAnyConnection(
            @Param("postId") Long postId,
            @Param("connectionIds") List<Long> connectionIds
    );
}

