package com.linkedin.post.service;

import com.linkedin.post.dto.FeedResponseDto;
import com.linkedin.post.dto.PostResponseDto;
import com.linkedin.post.model.Post;
import com.linkedin.post.model.PostVisibility;
import com.linkedin.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating personalized user feeds.
 * 
 * Purpose:
 * Creates customized content feeds for users based on:
 * - Posts from connections
 * - User's own posts
 * - Public posts (for discovery)
 * - Trending content
 * 
 * Design Pattern: Service Layer Pattern
 * - Encapsulates feed generation logic
 * - Coordinates with multiple services
 * - Applies caching for performance
 * 
 * Feed Algorithm (Simplified):
 * 1. Fetch user's connections from Connection Service
 * 2. Get posts by connections (visibility: PUBLIC or CONNECTIONS_ONLY)
 * 3. Add user's own posts (all visibility levels)
 * 4. Sort by recency (newest first)
 * 5. Apply pagination
 * 6. Enrich with user data
 * 
 * Future Enhancements:
 * - Ranking algorithm (not just chronological)
 * - Personalization based on interests
 * - Engagement-based sorting
 * - Sponsored content
 * - "You might like" suggestions
 * 
 * Flow Example:
 * <pre>
 * User opens LinkedIn
 *   ↓
 * Frontend: GET /api/feed
 *   ↓
 * FeedService.getPersonalizedFeed(userId, pageable)
 *   ↓
 * 1. Get user's connections (Feign → Connection Service)
 * 2. Query posts by connections
 * 3. Add user's own posts
 * 4. Sort by createdAt DESC
 * 5. Enrich with author data (Feign → User Service)
 * 6. Return feed
 *   ↓
 * Frontend displays posts in chronological order
 * </pre>
 * 
 * @see FeedResponseDto
 * @see PostRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedService {

    private final PostRepository postRepository;
    private final com.linkedin.post.client.ConnectionServiceClient connectionServiceClient;
    private final com.linkedin.post.mapper.PostMapper postMapper;
    private final PostService postService;

    /**
     * Generate personalized feed for a user.
     * 
     * Algorithm:
     * 1. Fetch user's connections from Connection Service
     * 2. Query posts where:
     *    - Author is a connection AND visibility is PUBLIC or CONNECTIONS_ONLY
     *    - OR author is the user (own posts)
     * 3. Sort by recency (newest first)
     * 4. Apply pagination
     * 5. Enrich posts with user data
     * 6. Return feed with metadata
     * 
     * Caching:
     * - Feed is cached for 5 minutes (configured in application.yml)
     * - Cache is invalidated when:
     *   - User creates a post
     *   - User's connection creates a post
     *   - User adds/removes connections
     * 
     * @param userId Current user ID
     * @param pageable Pagination parameters
     * @return FeedResponseDto with posts and metadata
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "userFeed", key = "#userId + '_' + #pageable.pageNumber", unless = "#result == null")
    public FeedResponseDto getPersonalizedFeed(Long userId, Pageable pageable) {
        log.info("Generating feed for user {}", userId);

        // 1. Fetch user's connections from Connection Service
        List<Long> connectionIds;
        try {
            connectionIds = connectionServiceClient.getConnectionIds(userId);
        } catch (Exception e) {
            log.error("Failed to fetch connections for user {}: {}", userId, e.getMessage());
            connectionIds = List.of(); // Fallback to empty list
        }

        log.debug("User {} has {} connections", userId, connectionIds.size());

        // 2. Query posts for feed
        Page<Post> posts;
        if (connectionIds.isEmpty()) {
            // No connections: show only user's own posts
            log.debug("No connections found, showing only user's posts");
            posts = postRepository.findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(userId, pageable);
        } else {
            // Has connections: show connection posts + own posts
            posts = postRepository.findFeedPosts(userId, connectionIds, pageable);
        }

        log.debug("Found {} posts for user {} feed", posts.getTotalElements(), userId);

        // 3. Map to DTOs and enrich
        List<PostResponseDto> postDtos = posts.stream()
                .map(post -> postService.getPostById(post.getId(), userId))
                .toList();

        // 4. Build pagination metadata
        FeedResponseDto.PaginationDto pagination = FeedResponseDto.PaginationDto.builder()
                .page(posts.getNumber())
                .size(posts.getSize())
                .totalElements(posts.getTotalElements())
                .totalPages(posts.getTotalPages())
                .hasNext(posts.hasNext())
                .hasPrevious(posts.hasPrevious())
                .build();

        // 5. Build feed metadata
        FeedResponseDto.FeedMetadataDto metadata = FeedResponseDto.FeedMetadataDto.builder()
                .connectionsCount(connectionIds.size())
                .postsInFeed(posts.getTotalElements())
                .lastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();

        // 6. Build feed response
        FeedResponseDto feed = FeedResponseDto.builder()
                .posts(postDtos)
                .pagination(pagination)
                .feedMetadata(metadata)
                .build();

        log.info("Generated feed for user {} with {} posts", userId, postDtos.size());
        return feed;
    }

    /**
     * Get public feed (for non-logged-in users).
     * 
     * Shows only PUBLIC posts, sorted by recency.
     * 
     * @param pageable Pagination parameters
     * @return Page of PostResponseDto
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "publicFeed", key = "#pageable.pageNumber")
    public Page<PostResponseDto> getPublicFeed(Pageable pageable) {
        log.debug("Fetching public feed");

        Page<Post> posts = postRepository.findByVisibilityAndDeletedFalseOrderByCreatedAtDesc(
                PostVisibility.PUBLIC,
                pageable
        );

        return posts.map(post -> {
            PostResponseDto dto = postMapper.toDto(post);
            // For public feed, no user context for enrichment
            return dto;
        });
    }

    /**
     * Get feed for a specific hashtag.
     * 
     * Shows posts with the specified hashtag, filtered by visibility.
     * 
     * @param hashtag Hashtag to filter by (without #)
     * @param userId Current user ID (for visibility filtering)
     * @param pageable Pagination parameters
     * @return Page of PostResponseDto
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "hashtagFeed", key = "#hashtag + '_' + #userId + '_' + #pageable.pageNumber")
    public Page<PostResponseDto> getHashtagFeed(String hashtag, Long userId, Pageable pageable) {
        log.debug("Fetching feed for hashtag: #{}", hashtag);

        Page<Post> posts = postRepository.findByHashtag(hashtag, pageable);

        // Filter by visibility and enrich
        return posts.map(post -> postService.getPostById(post.getId(), userId));
    }

    /**
     * Refresh feed cache for a user.
     * 
     * Called when:
     * - User creates a post
     * - User adds/removes connections
     * - Connection creates a post
     * 
     * @param userId User ID
     */
    public void refreshFeedCache(Long userId) {
        log.info("Refreshing feed cache for user {}", userId);
        // Cache eviction is handled by @CacheEvict annotations in PostService
        // This method is for explicit cache refresh if needed
    }
}

