package com.linkedin.post.service;

import com.linkedin.common.exceptions.AccessDeniedException;
import com.linkedin.common.exceptions.ResourceNotFoundException;
import com.linkedin.post.dto.CommentResponseDto;
import com.linkedin.post.dto.CreateCommentRequest;
import com.linkedin.post.dto.UpdateCommentRequest;
import com.linkedin.post.mapper.CommentMapper;
import com.linkedin.post.model.Comment;
import com.linkedin.post.model.Post;
import com.linkedin.post.repository.CommentRepository;
import com.linkedin.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing comments on posts.
 * 
 * Purpose:
 * Handles all comment-related business logic:
 * - Create comments (root and replies)
 * - Update comments
 * - Delete comments (with cascade)
 * - Retrieve comments with nested structure
 * 
 * Design Pattern: Service Layer Pattern + Composite Pattern
 * - Encapsulates business logic
 * - Manages nested comment structure
 * - Coordinates between repositories
 * 
 * Business Rules:
 * 1. Can only comment on non-deleted posts
 * 2. Can only reply to non-deleted comments
 * 3. Only author can edit their own comment
 * 4. Author or post author can delete comment
 * 5. Deleting comment also deletes all replies (cascade)
 * 6. Editing comment sets edited flag
 * 
 * Comment Structure:
 * <pre>
 * Post
 *   ├─ Comment A (root, parent=NULL)
 *   │    ├─ Reply B (parent=A)
 *   │    └─ Reply C (parent=A)
 *   │         └─ Reply D (parent=C)
 *   └─ Comment E (root, parent=NULL)
 * </pre>
 * 
 * Flow Example (Create Comment):
 * <pre>
 * User clicks "Comment" button
 *   ↓
 * Frontend: POST /api/posts/{id}/comments
 *   ↓
 * CommentService.createComment(request, postId, userId)
 *   ↓
 * 1. Validate post exists and not deleted
 * 2. If reply, validate parent comment exists
 * 3. Create Comment entity
 * 4. Increment post.commentsCount
 * 5. If reply, increment parent.repliesCount
 * 6. Publish CommentCreatedEvent to Kafka
 *   ↓
 * Notification Service receives event
 *   ↓
 * Notify post author: "John commented on your post"
 * </pre>
 * 
 * @see Comment
 * @see CommentRepository
 * @see CommentMapper
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final CommentMapper commentMapper;
    private final LikeService likeService;

    // =========================================================================
    // Create Comment
    // =========================================================================

    /**
     * Create a new comment on a post or reply to a comment.
     * 
     * Business Logic:
     * 1. Validate post exists and not deleted
     * 2. If reply (parentCommentId set):
     *    - Validate parent comment exists and not deleted
     *    - Validate parent is on the same post
     * 3. Create Comment entity
     * 4. Increment post.commentsCount
     * 5. If reply, increment parent.repliesCount
     * 6. Save comment
     * 
     * @param request CreateCommentRequest
     * @param postId Post ID
     * @param authorId Current user ID
     * @return CommentResponseDto
     * @throws ResourceNotFoundException if post or parent comment not found
     */
    @Transactional
    public CommentResponseDto createComment(CreateCommentRequest request, Long postId, Long authorId) {
        log.info("User {} creating comment on post {}", authorId, postId);

        // 1. Validate post exists
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found or deleted"));

        // 2. If reply, validate parent comment
        if (request.getParentCommentId() != null) {
            Comment parentComment = commentRepository.findByIdAndDeletedFalse(request.getParentCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found or deleted"));

            // Validate parent is on the same post
            if (!parentComment.getPostId().equals(postId)) {
                throw new IllegalStateException("Parent comment is not on this post");
            }

            log.debug("Creating reply to comment {}", request.getParentCommentId());
        }

        // 3. Create Comment entity
        Comment comment = commentMapper.toEntity(request);
        comment.setPostId(postId);
        comment.setAuthorId(authorId);

        // 4. Save comment
        Comment saved = commentRepository.save(comment);

        // 5. Increment post comments count
        postRepository.incrementCommentsCount(postId);

        // 6. If reply, increment parent replies count
        if (request.getParentCommentId() != null) {
            commentRepository.incrementRepliesCount(request.getParentCommentId());
        }

        log.info("Comment {} created successfully on post {}", saved.getId(), postId);

        // Note: Kafka event publishing can be added here when infrastructure is ready

        // Map to DTO
        CommentResponseDto dto = commentMapper.toDto(saved);
        enrichCommentDto(dto, authorId);
        return dto;
    }

    // =========================================================================
    // Update Comment
    // =========================================================================

    /**
     * Update an existing comment.
     * 
     * Business Logic:
     * 1. Validate comment exists and not deleted
     * 2. Check user is the author
     * 3. Update content
     * 4. Set edited flag and timestamp
     * 5. Save comment
     * 
     * @param commentId Comment ID
     * @param request UpdateCommentRequest
     * @param userId Current user ID
     * @return CommentResponseDto
     * @throws ResourceNotFoundException if comment not found
     * @throws AccessDeniedException if user is not author
     */
    @Transactional
    public CommentResponseDto updateComment(Long commentId, UpdateCommentRequest request, Long userId) {
        log.info("User {} updating comment {}", userId, commentId);

        // 1. Validate comment exists
        Comment comment = commentRepository.findByIdAndDeletedFalse(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found or deleted"));

        // 2. Check authorization
        if (!comment.canBeEditedBy(userId)) {
            log.warn("User {} attempted to edit comment {} without permission", userId, commentId);
            throw new AccessDeniedException("You can only edit your own comments");
        }

        // 3. Update content
        commentMapper.updateEntity(request, comment);

        // 4. Set edited flag
        comment.markAsEdited();

        // 5. Save
        Comment updated = commentRepository.save(comment);

        log.info("Comment {} updated successfully", commentId);

        // Map to DTO
        CommentResponseDto dto = commentMapper.toDto(updated);
        enrichCommentDto(dto, userId);
        return dto;
    }

    // =========================================================================
    // Delete Comment
    // =========================================================================

    /**
     * Delete a comment (soft delete).
     * 
     * Business Logic:
     * 1. Validate comment exists and not deleted
     * 2. Check user is author or post author
     * 3. Soft delete comment (keep for audit)
     * 4. Recursively soft delete all replies
     * 5. Decrement post.commentsCount
     * 6. If reply, decrement parent.repliesCount
     * 
     * Cascade Delete:
     * - If comment has replies, they are also deleted
     * - Maintains data integrity
     * 
     * @param commentId Comment ID
     * @param userId Current user ID
     * @throws ResourceNotFoundException if comment not found
     * @throws AccessDeniedException if user is not authorized
     */
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        log.info("User {} attempting to delete comment {}", userId, commentId);

        // 1. Validate comment exists
        Comment comment = commentRepository.findByIdAndDeletedFalse(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found or deleted"));

        // 2. Check authorization (author or post author can delete)
        Post post = postRepository.findById(comment.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!comment.canBeDeletedBy(userId, post.getAuthorId())) {
            log.warn("User {} attempted to delete comment {} without permission", userId, commentId);
            throw new AccessDeniedException("You can only delete your own comments or comments on your posts");
        }

        // 3. Soft delete comment
        comment.markAsDeleted();
        commentRepository.save(comment);

        // 4. Recursively delete all replies
        List<Comment> replies = commentRepository.findByParentCommentId(commentId);
        for (Comment reply : replies) {
            deleteCommentRecursive(reply);
        }

        // 5. Decrement post comments count
        postRepository.decrementCommentsCount(comment.getPostId());

        // 6. If reply, decrement parent replies count
        if (comment.getParentCommentId() != null) {
            commentRepository.decrementRepliesCount(comment.getParentCommentId());
        }

        log.info("Comment {} deleted successfully (including {} replies)", commentId, replies.size());

        // Note: Kafka event publishing can be added here when infrastructure is ready
    }

    /**
     * Recursive helper to delete replies.
     * 
     * @param comment Comment to delete
     */
    private void deleteCommentRecursive(Comment comment) {
        if (comment.isDeleted()) {
            return; // Already deleted
        }

        // Mark as deleted
        comment.markAsDeleted();
        commentRepository.save(comment);

        // Recursively delete child replies
        List<Comment> replies = commentRepository.findByParentCommentId(comment.getId());
        for (Comment reply : replies) {
            deleteCommentRecursive(reply);
        }

        log.debug("Recursively deleted comment {}", comment.getId());
    }

    // =========================================================================
    // Retrieve Comments
    // =========================================================================

    /**
     * Get a single comment by ID.
     * 
     * @param commentId Comment ID
     * @param userId Current user ID (for computed fields)
     * @return CommentResponseDto
     * @throws ResourceNotFoundException if comment not found
     */
    @Transactional(readOnly = true)
    public CommentResponseDto getCommentById(Long commentId, Long userId) {
        Comment comment = commentRepository.findByIdAndDeletedFalse(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        CommentResponseDto dto = commentMapper.toDto(comment);
        enrichCommentDto(dto, userId);
        return dto;
    }

    /**
     * Get root comments on a post (paginated).
     * 
     * Root comments are comments directly on the post, not replies.
     * 
     * @param postId Post ID
     * @param userId Current user ID
     * @param pageable Pagination parameters
     * @return Page of CommentResponseDto
     */
    @Transactional(readOnly = true)
    public Page<CommentResponseDto> getRootComments(Long postId, Long userId, Pageable pageable) {
        log.debug("Fetching root comments for post {}", postId);

        // Validate post exists
        if (!postRepository.existsByIdAndDeletedFalse(postId)) {
            throw new ResourceNotFoundException("Post not found");
        }

        Page<Comment> comments = commentRepository
                .findByPostIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAt(postId, pageable);

        return comments.map(comment -> {
            CommentResponseDto dto = commentMapper.toDto(comment);
            enrichCommentDto(dto, userId);
            return dto;
        });
    }

    /**
     * Get replies to a comment (paginated).
     * 
     * @param commentId Parent comment ID
     * @param userId Current user ID
     * @param pageable Pagination parameters
     * @return Page of CommentResponseDto
     */
    @Transactional(readOnly = true)
    public Page<CommentResponseDto> getReplies(Long commentId, Long userId, Pageable pageable) {
        log.debug("Fetching replies for comment {}", commentId);

        // Validate parent comment exists
        if (!commentRepository.existsByIdAndDeletedFalse(commentId)) {
            throw new ResourceNotFoundException("Comment not found");
        }

        Page<Comment> replies = commentRepository
                .findByParentCommentIdAndDeletedFalseOrderByCreatedAt(commentId, pageable);

        return replies.map(comment -> {
            CommentResponseDto dto = commentMapper.toDto(comment);
            enrichCommentDto(dto, userId);
            return dto;
        });
    }

    /**
     * Get all comments by a user (for profile page).
     * 
     * @param authorId Author's user ID
     * @param pageable Pagination parameters
     * @return Page of CommentResponseDto
     */
    @Transactional(readOnly = true)
    public Page<CommentResponseDto> getCommentsByUser(Long authorId, Pageable pageable) {
        log.debug("Fetching comments by user {}", authorId);

        Page<Comment> comments = commentRepository
                .findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(authorId, pageable);

        return comments.map(comment -> {
            CommentResponseDto dto = commentMapper.toDto(comment);
            enrichCommentDto(dto, authorId);
            return dto;
        });
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Enrich CommentResponseDto with computed fields.
     * 
     * Adds:
     * - hasLiked: Did current user like this comment?
     * - isAuthor: Is current user the author?
     * - canEdit: Can current user edit?
     * - canDelete: Can current user delete?
     * 
     * Note: author field (user data) will be enriched in controller/facade
     * 
     * @param dto CommentResponseDto to enrich
     * @param currentUserId Current user ID
     */
    private void enrichCommentDto(CommentResponseDto dto, Long currentUserId) {
        // Check if user liked
        dto.setHasLiked(likeService.hasUserLikedComment(dto.getId(), currentUserId));

        // Is current user the author?
        dto.setIsAuthor(dto.getAuthor() != null && dto.getAuthor().getId().equals(currentUserId));

        // Can edit? (only author)
        dto.setCanEdit(dto.getIsAuthor());

        // Can delete? (author or post author - post author checked in delete method)
        dto.setCanDelete(dto.getIsAuthor()); // Simplified, full check in deleteComment
    }
}

