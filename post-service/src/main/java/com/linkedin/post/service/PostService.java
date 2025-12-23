package com.linkedin.post.service;

import com.linkedin.common.exceptions.AccessDeniedException;
import com.linkedin.common.exceptions.ResourceNotFoundException;
import com.linkedin.post.dto.CreatePostRequest;
import com.linkedin.post.dto.PostResponseDto;
import com.linkedin.post.dto.UpdatePostRequest;
import com.linkedin.post.mapper.PostMapper;
import com.linkedin.post.model.Post;
import com.linkedin.post.model.PostVisibility;
import com.linkedin.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing posts.
 * 
 * Purpose:
 * Core service handling all post-related business logic:
 * - CRUD operations for posts
 * - Visibility checks
 * - Share/repost functionality
 * - Post statistics
 * - Enrichment with user data
 * 
 * Design Pattern: Service Layer Pattern
 * - Encapsulates complex business logic
 * - Coordinates multiple repositories and services
 * - Manages transactions and caching
 * - Enforces business rules
 * 
 * Business Rules:
 * 1. Posts must have valid content (1-3000 chars)
 * 2. Visibility controls who can see posts
 * 3. Only author can edit/delete their posts
 * 4. Deleted posts cannot be edited
 * 5. Hashtags and mentions auto-extracted from content
 * 6. Share count incremented when post is shared
 * 
 * Visibility Rules:
 * - PUBLIC: Everyone can see
 * - CONNECTIONS_ONLY: Only connections can see
 * - PRIVATE: Only author can see
 * 
 * Flow Example (Create Post):
 * <pre>
 * User clicks "Post" button
 *   ↓
 * Frontend: POST /api/posts
 *   ↓
 * PostService.createPost(request, userId)
 *   ↓
 * 1. Validate content
 * 2. Map request to entity
 * 3. Extract hashtags and mentions
 * 4. Set author ID
 * 5. Save to database
 * 6. Publish PostCreatedEvent to Kafka
 *   ↓
 * Feed Service updates feeds
 * Notification Service notifies mentioned users
 * </pre>
 * 
 * @see Post
 * @see PostRepository
 * @see PostMapper
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final PostMapper postMapper;
    private final ContentParserService contentParserService;
    private final LikeService likeService;
    private final com.linkedin.post.client.ConnectionServiceClient connectionServiceClient;
    private final com.linkedin.post.client.UserServiceClient userServiceClient;

    // =========================================================================
    // Create Post
    // =========================================================================

    /**
     * Create a new post.
     * 
     * Business Logic:
     * 1. Validate content
     * 2. Map request to entity
     * 3. Set author ID
     * 4. Extract hashtags from content
     * 5. Extract mentions from content
     * 6. Save post
     * 7. Publish PostCreatedEvent to Kafka
     * 
     * @param request CreatePostRequest
     * @param authorId Current user ID
     * @return PostResponseDto
     */
    @Transactional
    @CacheEvict(value = "userPosts", key = "#authorId")
    public PostResponseDto createPost(CreatePostRequest request, Long authorId) {
        log.info("User {} creating post of type {}", authorId, request.getType());

        // 1. Validate content
        if (!contentParserService.isValidContent(request.getContent())) {
            throw new IllegalArgumentException("Invalid post content");
        }

        // 2. Clean content
        String cleanedContent = contentParserService.cleanContent(request.getContent());

        // 3. Map to entity
        Post post = postMapper.toEntity(request);
        post.setContent(cleanedContent);
        post.setAuthorId(authorId);

        // 4. Extract hashtags
        List<String> hashtags = contentParserService.extractHashtags(cleanedContent);
        post.setHashtagList(hashtags);
        log.debug("Extracted {} hashtags: {}", hashtags.size(), hashtags);

        // 5. Extract mentions
        List<Long> mentions = contentParserService.extractMentions(cleanedContent);
        post.setMentionList(mentions);
        log.debug("Extracted {} mentions: {}", mentions.size(), mentions);

        // 6. If sharing a post, increment original post's share count
        if (request.getSharedPostId() != null) {
            postRepository.incrementSharesCount(request.getSharedPostId());
            log.debug("Incremented share count for original post {}", request.getSharedPostId());
        }

        // 7. Save post
        Post saved = postRepository.save(post);
        log.info("Post {} created successfully by user {}", saved.getId(), authorId);

        // Note: Kafka event publishing can be added here when Kafka infrastructure is ready
        // Example: kafkaProducer.send("post-created", new PostCreatedEvent(saved));

        // Map to DTO and enrich
        PostResponseDto dto = postMapper.toDto(saved);
        enrichPostDto(dto, authorId);
        return dto;
    }

    // =========================================================================
    // Read Post
    // =========================================================================

    /**
     * Get a post by ID with visibility check.
     * 
     * Business Logic:
     * 1. Fetch post from database
     * 2. Check if current user can see this post (visibility rules)
     * 3. Increment view count
     * 4. Enrich with user data
     * 5. Return DTO
     * 
     * @param postId Post ID
     * @param currentUserId Current user ID
     * @return PostResponseDto
     * @throws ResourceNotFoundException if post not found
     * @throws AccessDeniedException if user cannot view post
     */
    @Transactional
    @Cacheable(value = "posts", key = "#postId")
    public PostResponseDto getPostById(Long postId, Long currentUserId) {
        log.debug("User {} requesting post {}", currentUserId, postId);

        // 1. Fetch post
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found or deleted"));

        // 2. Check visibility
        if (!canUserViewPost(post, currentUserId)) {
            log.warn("User {} attempted to view post {} without permission", currentUserId, postId);
            throw new AccessDeniedException("You do not have permission to view this post");
        }

        // 3. Increment view count (async in production)
        postRepository.incrementViewsCount(postId);

        // 4. Map to DTO and enrich
        PostResponseDto dto = postMapper.toDto(post);
        enrichPostDto(dto, currentUserId);

        // 5. If shared post, load original post
        if (post.isSharedPost()) {
            try {
                Post originalPost = postRepository.findByIdAndDeletedFalse(post.getSharedPostId())
                        .orElse(null);
                if (originalPost != null) {
                    PostResponseDto sharedPostDto = postMapper.toDto(originalPost);
                    enrichPostDto(sharedPostDto, currentUserId);
                    dto.setSharedPost(sharedPostDto);
                }
            } catch (Exception e) {
                log.warn("Failed to load shared post {}: {}", post.getSharedPostId(), e.getMessage());
                // Continue without shared post data
            }
        }

        return dto;
    }

    /**
     * Get posts by a specific user (profile page).
     * 
     * Applies visibility filtering based on current user.
     * 
     * @param authorId Author's user ID
     * @param currentUserId Current user ID (viewer)
     * @param pageable Pagination parameters
     * @return Page of PostResponseDto
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "userPosts", key = "#authorId + '_' + #currentUserId + '_' + #pageable.pageNumber")
    public Page<PostResponseDto> getPostsByUser(Long authorId, Long currentUserId, Pageable pageable) {
        log.debug("User {} requesting posts by user {}", currentUserId, authorId);

        Page<Post> posts;

        if (authorId.equals(currentUserId)) {
            // Viewing own posts: show all
            posts = postRepository.findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(authorId, pageable);
        } else {
            // Check if users are connected via ConnectionService
            boolean isConnection = false;
            try {
                isConnection = connectionServiceClient.areConnected(currentUserId, authorId);
            } catch (Exception e) {
                log.error("Failed to check connection status: {}", e.getMessage());
            }

            if (isConnection) {
                // Connected: show PUBLIC and CONNECTIONS_ONLY posts
                posts = postRepository.findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(authorId, pageable);
            } else {
                // Not connected: only show PUBLIC posts
                posts = postRepository.findByAuthorIdAndVisibilityAndDeletedFalseOrderByCreatedAtDesc(
                        authorId, PostVisibility.PUBLIC, pageable);
            }
        }

        return posts.map(post -> {
            PostResponseDto dto = postMapper.toDto(post);
            enrichPostDto(dto, currentUserId);
            return dto;
        });
    }

    // =========================================================================
    // Update Post
    // =========================================================================

    /**
     * Update an existing post.
     * 
     * Business Logic:
     * 1. Fetch post
     * 2. Check user is the author
     * 3. Update content and visibility
     * 4. Re-extract hashtags and mentions
     * 5. Save post
     * 
     * @param postId Post ID
     * @param request UpdatePostRequest
     * @param userId Current user ID
     * @return PostResponseDto
     * @throws ResourceNotFoundException if post not found
     * @throws AccessDeniedException if user is not author
     */
    @Transactional
    @CacheEvict(value = {"posts", "userPosts"}, allEntries = true)
    public PostResponseDto updatePost(Long postId, UpdatePostRequest request, Long userId) {
        log.info("User {} updating post {}", userId, postId);

        // 1. Fetch post
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found or deleted"));

        // 2. Check authorization
        if (!post.canBeEditedBy(userId)) {
            log.warn("User {} attempted to edit post {} without permission", userId, postId);
            throw new AccessDeniedException("You can only edit your own posts");
        }

        // 3. Clean content
        String cleanedContent = contentParserService.cleanContent(request.getContent());

        // 4. Update entity
        postMapper.updateEntity(request, post);
        post.setContent(cleanedContent);

        // 5. Re-extract hashtags and mentions
        List<String> hashtags = contentParserService.extractHashtags(cleanedContent);
        post.setHashtagList(hashtags);

        List<Long> mentions = contentParserService.extractMentions(cleanedContent);
        post.setMentionList(mentions);

        // 6. Save
        Post updated = postRepository.save(post);
        log.info("Post {} updated successfully", postId);

        // Note: Kafka event publishing can be added here when infrastructure is ready

        // Map to DTO and enrich
        PostResponseDto dto = postMapper.toDto(updated);
        enrichPostDto(dto, userId);
        return dto;
    }

    // =========================================================================
    // Delete Post
    // =========================================================================

    /**
     * Delete a post (soft delete).
     * 
     * Business Logic:
     * 1. Fetch post
     * 2. Check user is the author
     * 3. Soft delete post (set deleted flag)
     * 4. Keep data for audit purposes
     * 
     * Note: Comments and likes are NOT deleted (they reference deleted post)
     * 
     * @param postId Post ID
     * @param userId Current user ID
     * @throws ResourceNotFoundException if post not found
     * @throws AccessDeniedException if user is not author
     */
    @Transactional
    @CacheEvict(value = {"posts", "userPosts"}, allEntries = true)
    public void deletePost(Long postId, Long userId) {
        log.info("User {} deleting post {}", userId, postId);

        // 1. Fetch post
        Post post = postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found or deleted"));

        // 2. Check authorization
        if (!post.canBeDeletedBy(userId)) {
            log.warn("User {} attempted to delete post {} without permission", userId, postId);
            throw new AccessDeniedException("You can only delete your own posts");
        }

        // 3. Soft delete
        post.markAsDeleted();
        postRepository.save(post);

        log.info("Post {} deleted successfully", postId);

        // Note: Kafka event publishing can be added here when infrastructure is ready
    }

    // =========================================================================
    // Search & Discovery
    // =========================================================================

    /**
     * Search posts by hashtag.
     * 
     * @param hashtag Hashtag to search (without # symbol)
     * @param currentUserId Current user ID (for visibility filtering)
     * @param pageable Pagination parameters
     * @return Page of PostResponseDto
     */
    @Transactional(readOnly = true)
    public Page<PostResponseDto> searchByHashtag(String hashtag, Long currentUserId, Pageable pageable) {
        log.debug("Searching posts by hashtag: #{}", hashtag);

        Page<Post> posts = postRepository.findByHashtag(hashtag, pageable);

        // Filter by visibility and map to DTO
        return posts
                .filter(post -> canUserViewPost(post, currentUserId))
                .map(post -> {
                    PostResponseDto dto = postMapper.toDto(post);
                    enrichPostDto(dto, currentUserId);
                    return dto;
                });
    }

    /**
     * Search posts by content (simple text search).
     * 
     * @param searchTerm Search term
     * @param currentUserId Current user ID
     * @param pageable Pagination parameters
     * @return Page of PostResponseDto
     */
    @Transactional(readOnly = true)
    public Page<PostResponseDto> searchPosts(String searchTerm, Long currentUserId, Pageable pageable) {
        log.debug("Searching posts with term: {}", searchTerm);

        Page<Post> posts = postRepository.searchByContent(searchTerm, pageable);

        return posts
                .filter(post -> canUserViewPost(post, currentUserId))
                .map(post -> {
                    PostResponseDto dto = postMapper.toDto(post);
                    enrichPostDto(dto, currentUserId);
                    return dto;
                });
    }

    /**
     * Get trending posts (high engagement in last 24 hours).
     * 
     * @param currentUserId Current user ID
     * @param pageable Pagination parameters
     * @return List of PostResponseDto
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "trendingPosts", key = "#currentUserId")
    public List<PostResponseDto> getTrendingPosts(Long currentUserId, Pageable pageable) {
        log.debug("Fetching trending posts");

        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<Post> posts = postRepository.findTrendingPosts(since, pageable);

        return posts.stream()
                .filter(post -> canUserViewPost(post, currentUserId))
                .map(post -> {
                    PostResponseDto dto = postMapper.toDto(post);
                    enrichPostDto(dto, currentUserId);
                    return dto;
                })
                .toList();
    }

    // =========================================================================
    // Visibility Checks
    // =========================================================================

    /**
     * Check if a user can view a specific post.
     * 
     * Visibility Rules:
     * 1. PUBLIC: Everyone can see
     * 2. CONNECTIONS_ONLY: Only connections can see
     * 3. PRIVATE: Only author can see
     * 
     * @param post Post to check
     * @param userId User ID
     * @return true if user can view
     */
    private boolean canUserViewPost(Post post, Long userId) {
        // Author can always see their own post
        if (post.getAuthorId().equals(userId)) {
            return true;
        }

        // Check visibility
        switch (post.getVisibility()) {
            case PUBLIC:
                return true;
            case CONNECTIONS_ONLY:
                // Check if users are connected via ConnectionService
                try {
                    return connectionServiceClient.areConnected(userId, post.getAuthorId());
                } catch (Exception e) {
                    log.error("Failed to check connection status: {}", e.getMessage());
                    return false; // Fail closed
                }
            case PRIVATE:
                return false; // Only author can see
            default:
                return false;
        }
    }

    // =========================================================================
    // Enrichment
    // =========================================================================

    /**
     * Enrich PostResponseDto with computed fields.
     * 
     * Adds:
     * - hasLiked: Did current user like this post?
     * - isAuthor: Is current user the author?
     * - canEdit: Can current user edit?
     * - canDelete: Can current user delete?
     * 
     * Note: author field (user data) will be enriched in controller/facade
     * via UserServiceClient (Feign)
     * 
     * @param dto PostResponseDto to enrich
     * @param currentUserId Current user ID
     */
    private void enrichPostDto(PostResponseDto dto, Long currentUserId) {
        // Check if user liked
        dto.setHasLiked(likeService.hasUserLikedPost(dto.getId(), currentUserId));

        // Fetch author data from User Service
        try {
            com.linkedin.post.client.UserResponse author = userServiceClient.getUserById(dto.getId());
            PostResponseDto.AuthorDto authorDto = PostResponseDto.AuthorDto.builder()
                    .id(author.getId())
                    .displayName(author.getDisplayName())
                    .headline(author.getHeadline())
                    .profilePictureUrl(author.getProfilePictureUrl())
                    .build();
            dto.setAuthor(authorDto);
            
            // Compute permissions
            dto.setIsAuthor(currentUserId != null && currentUserId.equals(author.getId()));
            dto.setCanEdit(dto.getIsAuthor());
            dto.setCanDelete(dto.getIsAuthor());
        } catch (Exception e) {
            log.error("Failed to fetch author data for post {}: {}", dto.getId(), e.getMessage());
            // Continue without author data
            dto.setIsAuthor(false);
            dto.setCanEdit(false);
            dto.setCanDelete(false);
        }
    }
}

