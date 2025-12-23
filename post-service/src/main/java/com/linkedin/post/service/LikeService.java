package com.linkedin.post.service;

import com.linkedin.common.exceptions.ResourceNotFoundException;
import com.linkedin.post.model.Comment;
import com.linkedin.post.model.Like;
import com.linkedin.post.model.Post;
import com.linkedin.post.repository.CommentRepository;
import com.linkedin.post.repository.LikeRepository;
import com.linkedin.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing likes on posts and comments.
 * 
 * Purpose:
 * Handles all like-related business logic:
 * - Like a post
 * - Unlike a post
 * - Like a comment
 * - Unlike a comment
 * - Check if user liked something
 * 
 * Design Pattern: Service Layer Pattern
 * - Encapsulates business logic
 * - Coordinates between repositories
 * - Manages transactions
 * 
 * Business Rules:
 * 1. User cannot like their own post
 * 2. User can only like once (enforced by DB unique constraint)
 * 3. Liking increments counter
 * 4. Unliking decrements counter
 * 5. Cannot like deleted posts/comments
 * 
 * Flow Example (Like a Post):
 * <pre>
 * User clicks "Like" button
 *   ↓
 * Frontend: POST /api/posts/{id}/like
 *   ↓
 * LikeService.likePost(postId, userId)
 *   ↓
 * 1. Check post exists and not deleted
 * 2. Check user is not the author
 * 3. Check not already liked
 * 4. Create Like record
 * 5. Increment post.likesCount
 * 6. Publish PostLikedEvent to Kafka
 *   ↓
 * Notification Service receives event
 *   ↓
 * Notify post author: "John liked your post"
 * </pre>
 * 
 * @see Like
 * @see LikeRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    // =========================================================================
    // Post Likes
    // =========================================================================

    /**
     * Like a post.
     * 
     * Business Logic:
     * 1. Validate post exists and not deleted
     * 2. Check user is not the author (can't like own post)
     * 3. Check not already liked
     * 4. Create Like record
     * 5. Increment post.likesCount
     * 
     * @param postId Post ID
     * @param userId Current user ID
     * @throws ResourceNotFoundException if post not found
     * @throws IllegalStateException if already liked or own post
     */
    @Transactional
    public void likePost(Long postId, Long userId) {
        log.info("User {} attempting to like post {}", userId, postId);

        // 1. Validate post exists
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found or deleted"));

        // 2. Check user is not the author
        if (post.getAuthorId().equals(userId)) {
            log.warn("User {} attempted to like their own post {}", userId, postId);
            throw new IllegalStateException("Cannot like your own post");
        }

        // 3. Check not already liked
        boolean alreadyLiked = likeRepository.existsByUserIdAndPostIdAndCommentIdIsNull(userId, postId);
        if (alreadyLiked) {
            log.warn("User {} already liked post {}", userId, postId);
            throw new IllegalStateException("Post already liked");
        }

        // 4. Create Like record
        Like like = Like.builder()
                .userId(userId)
                .postId(postId)
                .commentId(null) // Post like, not comment like
                .build();
        likeRepository.save(like);

        // 5. Increment likes count (denormalized counter)
        postRepository.incrementLikesCount(postId);

        log.info("User {} successfully liked post {}", userId, postId);
        
        // Note: Kafka event publishing can be added here when infrastructure is ready
    }

    /**
     * Unlike a post.
     * 
     * Business Logic:
     * 1. Validate post exists
     * 2. Check user has liked this post
     * 3. Delete Like record
     * 4. Decrement post.likesCount
     * 
     * @param postId Post ID
     * @param userId Current user ID
     * @throws ResourceNotFoundException if post not found
     * @throws IllegalStateException if not liked
     */
    @Transactional
    public void unlikePost(Long postId, Long userId) {
        log.info("User {} attempting to unlike post {}", userId, postId);

        // 1. Validate post exists (even deleted posts can be unliked)
        if (!postRepository.existsById(postId)) {
            throw new ResourceNotFoundException("Post not found");
        }

        // 2. Check user has liked this post
        boolean hasLiked = likeRepository.existsByUserIdAndPostIdAndCommentIdIsNull(userId, postId);
        if (!hasLiked) {
            log.warn("User {} has not liked post {}", userId, postId);
            throw new IllegalStateException("Post not liked");
        }

        // 3. Delete Like record
        int deleted = likeRepository.deleteByUserIdAndPostIdAndCommentIdIsNull(userId, postId);
        if (deleted == 0) {
            throw new IllegalStateException("Failed to unlike post");
        }

        // 4. Decrement likes count
        postRepository.decrementLikesCount(postId);

        log.info("User {} successfully unliked post {}", userId, postId);
        
        // Note: Kafka event publishing (optional) can be added here when infrastructure is ready
    }

    /**
     * Check if user has liked a post.
     * 
     * @param postId Post ID
     * @param userId User ID
     * @return true if liked
     */
    @Transactional(readOnly = true)
    public boolean hasUserLikedPost(Long postId, Long userId) {
        return likeRepository.existsByUserIdAndPostIdAndCommentIdIsNull(userId, postId);
    }

    // =========================================================================
    // Comment Likes
    // =========================================================================

    /**
     * Like a comment.
     * 
     * Similar to liking a post, but for comments.
     * 
     * Business Logic:
     * 1. Validate comment exists and not deleted
     * 2. Check user is not the comment author
     * 3. Check not already liked
     * 4. Create Like record
     * 5. Increment comment.likesCount
     * 
     * @param postId Post ID (where comment is)
     * @param commentId Comment ID
     * @param userId Current user ID
     * @throws ResourceNotFoundException if comment not found
     * @throws IllegalStateException if already liked or own comment
     */
    @Transactional
    public void likeComment(Long postId, Long commentId, Long userId) {
        log.info("User {} attempting to like comment {} on post {}", userId, commentId, postId);

        // 1. Validate comment exists
        Comment comment = commentRepository.findByIdAndDeletedFalse(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found or deleted"));

        // Validate comment is on the specified post
        if (!comment.getPostId().equals(postId)) {
            throw new IllegalStateException("Comment does not belong to this post");
        }

        // 2. Check user is not the author
        if (comment.getAuthorId().equals(userId)) {
            log.warn("User {} attempted to like their own comment {}", userId, commentId);
            throw new IllegalStateException("Cannot like your own comment");
        }

        // 3. Check not already liked
        boolean alreadyLiked = likeRepository.existsByUserIdAndCommentId(userId, commentId);
        if (alreadyLiked) {
            log.warn("User {} already liked comment {}", userId, commentId);
            throw new IllegalStateException("Comment already liked");
        }

        // 4. Create Like record
        Like like = Like.builder()
                .userId(userId)
                .postId(postId)
                .commentId(commentId)
                .build();
        likeRepository.save(like);

        // 5. Increment likes count
        commentRepository.incrementLikesCount(commentId);

        log.info("User {} successfully liked comment {}", userId, commentId);
        
        // Note: Kafka event publishing can be added here when infrastructure is ready
    }

    /**
     * Unlike a comment.
     * 
     * Business Logic:
     * 1. Validate comment exists
     * 2. Check user has liked this comment
     * 3. Delete Like record
     * 4. Decrement comment.likesCount
     * 
     * @param postId Post ID
     * @param commentId Comment ID
     * @param userId Current user ID
     * @throws ResourceNotFoundException if comment not found
     * @throws IllegalStateException if not liked
     */
    @Transactional
    public void unlikeComment(Long postId, Long commentId, Long userId) {
        log.info("User {} attempting to unlike comment {} on post {}", userId, commentId, postId);

        // 1. Validate comment exists
        if (!commentRepository.existsById(commentId)) {
            throw new ResourceNotFoundException("Comment not found");
        }

        // 2. Check user has liked this comment
        boolean hasLiked = likeRepository.existsByUserIdAndCommentId(userId, commentId);
        if (!hasLiked) {
            log.warn("User {} has not liked comment {}", userId, commentId);
            throw new IllegalStateException("Comment not liked");
        }

        // 3. Delete Like record
        int deleted = likeRepository.deleteByUserIdAndPostIdAndCommentId(userId, postId, commentId);
        if (deleted == 0) {
            throw new IllegalStateException("Failed to unlike comment");
        }

        // 4. Decrement likes count
        commentRepository.decrementLikesCount(commentId);

        log.info("User {} successfully unliked comment {}", userId, commentId);
    }

    /**
     * Check if user has liked a comment.
     * 
     * @param commentId Comment ID
     * @param userId User ID
     * @return true if liked
     */
    @Transactional(readOnly = true)
    public boolean hasUserLikedComment(Long commentId, Long userId) {
        return likeRepository.existsByUserIdAndCommentId(userId, commentId);
    }

    // =========================================================================
    // Toggle Like (Convenience Methods)
    // =========================================================================

    /**
     * Toggle like on a post.
     * 
     * If liked: Unlike
     * If not liked: Like
     * 
     * This is a convenience method for frontend toggle buttons.
     * 
     * @param postId Post ID
     * @param userId User ID
     * @return true if now liked, false if now unliked
     */
    @Transactional
    public boolean togglePostLike(Long postId, Long userId) {
        boolean hasLiked = hasUserLikedPost(postId, userId);
        
        if (hasLiked) {
            unlikePost(postId, userId);
            return false; // Now unliked
        } else {
            likePost(postId, userId);
            return true; // Now liked
        }
    }

    /**
     * Toggle like on a comment.
     * 
     * @param postId Post ID
     * @param commentId Comment ID
     * @param userId User ID
     * @return true if now liked, false if now unliked
     */
    @Transactional
    public boolean toggleCommentLike(Long postId, Long commentId, Long userId) {
        boolean hasLiked = hasUserLikedComment(commentId, userId);
        
        if (hasLiked) {
            unlikeComment(postId, commentId, userId);
            return false; // Now unliked
        } else {
            likeComment(postId, commentId, userId);
            return true; // Now liked
        }
    }
}

