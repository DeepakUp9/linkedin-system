package com.linkedin.post.repository;

import com.linkedin.post.model.Post;
import com.linkedin.post.model.PostVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for {@link Post} entities.
 * 
 * Purpose:
 * Provides database access methods for posts with:
 * - Basic CRUD operations (from JpaRepository)
 * - Custom query methods (Spring Data magic)
 * - Complex queries with @Query annotation
 * - Pagination support
 * 
 * Design Pattern: Repository Pattern
 * - Abstracts data access logic
 * - Hides database implementation details
 * - Makes testing easier (can mock repository)
 * 
 * How Spring Data JPA Works:
 * 
 * 1. Method Name Parsing (Spring Magic!):
 * <pre>
 * findByAuthorId(Long authorId)
 *   ↓ Spring parses method name
 * SELECT * FROM posts WHERE author_id = ?
 * 
 * findByAuthorIdAndDeletedFalse(Long authorId)
 *   ↓ Spring parses method name
 * SELECT * FROM posts WHERE author_id = ? AND is_deleted = FALSE
 * </pre>
 * 
 * 2. @Query Annotation (Custom SQL):
 * <pre>
 * {@literal @}Query("SELECT p FROM Post p WHERE p.authorId = :authorId")
 * List<Post> customQuery(@Param("authorId") Long authorId);
 * </pre>
 * 
 * 3. Pagination (Spring Data):
 * <pre>
 * Pageable pageable = PageRequest.of(0, 20); // page 0, size 20
 * Page<Post> posts = postRepository.findAll(pageable);
 * </pre>
 * 
 * Usage Example:
 * <pre>
 * {@code
 * // Inject repository
 * @Autowired
 * private PostRepository postRepository;
 * 
 * // Find user's posts
 * List<Post> posts = postRepository.findByAuthorIdAndDeletedFalse(123L);
 * 
 * // Get trending posts
 * Pageable pageable = PageRequest.of(0, 10);
 * List<Post> trending = postRepository.findTrendingPosts(
 *     LocalDateTime.now().minusHours(24),
 *     pageable
 * );
 * 
 * // Check if post exists
 * boolean exists = postRepository.existsByIdAndDeletedFalse(456L);
 * }
 * </pre>
 * 
 * @see Post
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // =========================================================================
    // Basic Queries (Spring Data Method Name Parsing)
    // =========================================================================
    
    /**
     * Find a post by ID, excluding deleted posts.
     * 
     * Spring Data generates:
     * SELECT * FROM posts WHERE id = ? AND is_deleted = FALSE
     * 
     * @param id Post ID
     * @return Optional containing post if found and not deleted
     */
    Optional<Post> findByIdAndDeletedFalse(Long id);
    
    /**
     * Find all posts by an author, excluding deleted posts.
     * 
     * Spring Data generates:
     * SELECT * FROM posts WHERE author_id = ? AND is_deleted = FALSE
     * ORDER BY created_at DESC
     * 
     * Uses Index: idx_posts_author_deleted
     * 
     * @param authorId Author's user ID
     * @param pageable Pagination parameters
     * @return Page of posts
     */
    Page<Post> findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(
            Long authorId, 
            Pageable pageable
    );
    
    /**
     * Find all posts by an author with specific visibility, excluding deleted.
     * 
     * Use Case: Show only public posts on profile page for non-connections
     * 
     * @param authorId Author's user ID
     * @param visibility Visibility level
     * @param pageable Pagination parameters
     * @return Page of posts
     */
    Page<Post> findByAuthorIdAndVisibilityAndDeletedFalseOrderByCreatedAtDesc(
            Long authorId,
            PostVisibility visibility,
            Pageable pageable
    );
    
    /**
     * Find all public posts, excluding deleted, ordered by recency.
     * 
     * Use Case: Public feed for non-logged-in users
     * 
     * @param visibility PUBLIC visibility
     * @param pageable Pagination parameters
     * @return Page of public posts
     */
    Page<Post> findByVisibilityAndDeletedFalseOrderByCreatedAtDesc(
            PostVisibility visibility,
            Pageable pageable
    );
    
    /**
     * Check if a post exists and is not deleted.
     * 
     * Use Case: Quick validation before operations
     * 
     * @param id Post ID
     * @return true if post exists and not deleted
     */
    boolean existsByIdAndDeletedFalse(Long id);
    
    /**
     * Count non-deleted posts by author.
     * 
     * Use Case: Profile statistics
     * 
     * @param authorId Author's user ID
     * @return Count of posts
     */
    long countByAuthorIdAndDeletedFalse(Long authorId);

    // =========================================================================
    // Custom Queries with @Query (Complex Logic)
    // =========================================================================
    
    /**
     * Find trending posts based on engagement score in a time period.
     * 
     * Engagement Score = likes + (comments * 2) + (shares * 3)
     * - Comments weighted more than likes (higher engagement)
     * - Shares weighted most (highest engagement)
     * 
     * Use Case: "Trending Now" section
     * 
     * Native Query used for complex calculations.
     * 
     * @param since Start of time period (e.g., 24 hours ago)
     * @param pageable Pagination parameters
     * @return List of trending posts
     */
    @Query(value = """
        SELECT * FROM posts 
        WHERE is_deleted = FALSE 
        AND created_at >= :since
        ORDER BY (likes_count + comments_count * 2 + shares_count * 3) DESC
        """, 
        nativeQuery = true)
    List<Post> findTrendingPosts(
            @Param("since") LocalDateTime since,
            Pageable pageable
    );
    
    /**
     * Find posts for a user's feed (connections only + public posts).
     * 
     * Logic:
     * 1. Posts by user's connections (visibility = CONNECTIONS_ONLY or PUBLIC)
     * 2. User's own posts (all visibility levels)
     * 3. Exclude deleted posts
     * 4. Order by recency
     * 
     * Use Case: Personalized home feed
     * 
     * Note: connectionIds should come from Connection Service via Feign
     * 
     * @param userId Current user ID
     * @param connectionIds List of user's connection IDs
     * @param pageable Pagination parameters
     * @return Page of feed posts
     */
    @Query("""
        SELECT p FROM Post p 
        WHERE p.deleted = false 
        AND (
            (p.authorId IN :connectionIds AND p.visibility IN ('PUBLIC', 'CONNECTIONS_ONLY'))
            OR (p.authorId = :userId)
        )
        ORDER BY p.createdAt DESC
        """)
    Page<Post> findFeedPosts(
            @Param("userId") Long userId,
            @Param("connectionIds") List<Long> connectionIds,
            Pageable pageable
    );
    
    /**
     * Find posts mentioning a specific user.
     * 
     * Use Case: "Posts you're mentioned in" notifications
     * 
     * Note: mentions stored as comma-separated string "123,456,789"
     * Using LIKE for simple search (for production, consider JSON or array type)
     * 
     * @param userId User ID being mentioned
     * @param pageable Pagination parameters
     * @return Page of posts mentioning the user
     */
    @Query("""
        SELECT p FROM Post p 
        WHERE p.deleted = false 
        AND (
            p.mentions LIKE CONCAT('%', :userId, '%')
        )
        ORDER BY p.createdAt DESC
        """)
    Page<Post> findPostsMentioningUser(
            @Param("userId") Long userId,
            Pageable pageable
    );
    
    /**
     * Find posts by hashtag.
     * 
     * Use Case: Hashtag search (#NewJob, #Hiring, etc.)
     * 
     * Note: hashtags stored as comma-separated string
     * Case-insensitive search
     * 
     * @param hashtag Hashtag to search (without # symbol)
     * @param pageable Pagination parameters
     * @return Page of posts with the hashtag
     */
    @Query("""
        SELECT p FROM Post p 
        WHERE p.deleted = false 
        AND LOWER(p.hashtags) LIKE LOWER(CONCAT('%', :hashtag, '%'))
        ORDER BY p.createdAt DESC
        """)
    Page<Post> findByHashtag(
            @Param("hashtag") String hashtag,
            Pageable pageable
    );
    
    /**
     * Find all shares (reposts) of a specific post.
     * 
     * Use Case: "See who shared this post"
     * 
     * @param originalPostId ID of the original post
     * @param pageable Pagination parameters
     * @return Page of shares
     */
    Page<Post> findBySharedPostIdAndDeletedFalseOrderByCreatedAtDesc(
            Long originalPostId,
            Pageable pageable
    );
    
    /**
     * Search posts by content (simple text search).
     * 
     * Use Case: Search bar functionality
     * 
     * Note: For production, consider PostgreSQL Full-Text Search or Elasticsearch
     * 
     * @param searchTerm Text to search for
     * @param pageable Pagination parameters
     * @return Page of matching posts
     */
    @Query("""
        SELECT p FROM Post p 
        WHERE p.deleted = false 
        AND LOWER(p.content) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        ORDER BY p.createdAt DESC
        """)
    Page<Post> searchByContent(
            @Param("searchTerm") String searchTerm,
            Pageable pageable
    );

    // =========================================================================
    // Update Queries (Engagement Counters)
    // =========================================================================
    
    /**
     * Increment likes count for a post.
     * 
     * Called when: User likes a post
     * 
     * @Modifying indicates this query modifies data
     * 
     * @param postId Post ID
     */
    @Modifying
    @Query("UPDATE Post p SET p.likesCount = p.likesCount + 1 WHERE p.id = :postId")
    void incrementLikesCount(@Param("postId") Long postId);
    
    /**
     * Decrement likes count for a post.
     * 
     * Called when: User unlikes a post
     * 
     * @param postId Post ID
     */
    @Modifying
    @Query("UPDATE Post p SET p.likesCount = CASE WHEN p.likesCount > 0 THEN p.likesCount - 1 ELSE 0 END WHERE p.id = :postId")
    void decrementLikesCount(@Param("postId") Long postId);
    
    /**
     * Increment comments count for a post.
     * 
     * Called when: User adds a comment
     * 
     * @param postId Post ID
     */
    @Modifying
    @Query("UPDATE Post p SET p.commentsCount = p.commentsCount + 1 WHERE p.id = :postId")
    void incrementCommentsCount(@Param("postId") Long postId);
    
    /**
     * Decrement comments count for a post.
     * 
     * Called when: Comment is deleted
     * 
     * @param postId Post ID
     */
    @Modifying
    @Query("UPDATE Post p SET p.commentsCount = CASE WHEN p.commentsCount > 0 THEN p.commentsCount - 1 ELSE 0 END WHERE p.id = :postId")
    void decrementCommentsCount(@Param("postId") Long postId);
    
    /**
     * Increment shares count for a post.
     * 
     * Called when: User shares/reposts
     * 
     * @param postId Post ID
     */
    @Modifying
    @Query("UPDATE Post p SET p.sharesCount = p.sharesCount + 1 WHERE p.id = :postId")
    void incrementSharesCount(@Param("postId") Long postId);
    
    /**
     * Increment views count for a post.
     * 
     * Called when: User views a post (optional analytics)
     * 
     * @param postId Post ID
     */
    @Modifying
    @Query("UPDATE Post p SET p.viewsCount = p.viewsCount + 1 WHERE p.id = :postId")
    void incrementViewsCount(@Param("postId") Long postId);

    // =========================================================================
    // Batch Queries (Admin/Cleanup)
    // =========================================================================
    
    /**
     * Find deleted posts older than a specific date for cleanup.
     * 
     * Use Case: Scheduled job to permanently delete old soft-deleted posts
     * 
     * @param deletedBefore Date threshold
     * @return List of old deleted posts
     */
    List<Post> findByDeletedTrueAndDeletedAtBefore(LocalDateTime deletedBefore);
}

