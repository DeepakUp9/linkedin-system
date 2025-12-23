package com.linkedin.post.strategy;

import com.linkedin.post.model.Post;
import com.linkedin.post.model.PostVisibility;

/**
 * Strategy interface for checking post visibility.
 * 
 * Purpose:
 * Defines the contract for visibility checking algorithms.
 * Each visibility level (PUBLIC, CONNECTIONS_ONLY, PRIVATE) has its own strategy.
 * 
 * Design Pattern: Strategy Pattern
 * - Encapsulates different visibility algorithms
 * - Makes code more maintainable and testable
 * - Easy to add new visibility levels
 * - Follows Open/Closed Principle (open for extension, closed for modification)
 * 
 * Without Strategy Pattern:
 * <pre>
 * {@code
 * // Bad: Giant if-else or switch statement
 * public boolean canView(Post post, Long userId) {
 *     if (post.getVisibility() == PUBLIC) {
 *         return true;
 *     } else if (post.getVisibility() == CONNECTIONS_ONLY) {
 *         if (post.getAuthorId().equals(userId)) return true;
 *         return connectionService.areConnected(userId, post.getAuthorId());
 *     } else if (post.getVisibility() == PRIVATE) {
 *         return post.getAuthorId().equals(userId);
 *     }
 *     return false;
 * }
 * // Hard to test, hard to extend, hard to maintain
 * }
 * </pre>
 * 
 * With Strategy Pattern:
 * <pre>
 * {@code
 * // Good: Clean, testable, extensible
 * VisibilityStrategy strategy = strategyFactory.getStrategy(post.getVisibility());
 * return strategy.canView(post, userId);
 * }
 * </pre>
 * 
 * Usage Example:
 * <pre>
 * {@code
 * @Service
 * public class PostService {
 *     @Autowired
 *     private VisibilityStrategyFactory strategyFactory;
 *     
 *     public PostResponseDto getPost(Long postId, Long userId) {
 *         Post post = postRepository.findById(postId).orElseThrow();
 *         
 *         // Get strategy based on post visibility
 *         VisibilityStrategy strategy = strategyFactory.getStrategy(post.getVisibility());
 *         
 *         // Check if user can view
 *         if (!strategy.canView(post, userId)) {
 *             throw new AccessDeniedException("Cannot view this post");
 *         }
 *         
 *         return postMapper.toDto(post);
 *     }
 * }
 * }
 * </pre>
 * 
 * Adding New Visibility Level:
 * <pre>
 * {@code
 * // 1. Add enum value
 * public enum PostVisibility {
 *     PUBLIC, CONNECTIONS_ONLY, PRIVATE, PREMIUM_ONLY  // New!
 * }
 * 
 * // 2. Create strategy
 * @Component
 * public class PremiumOnlyVisibilityStrategy implements VisibilityStrategy {
 *     public boolean canView(Post post, Long userId) {
 *         return userService.isPremiumUser(userId);
 *     }
 *     public PostVisibility getSupportedVisibility() {
 *         return PostVisibility.PREMIUM_ONLY;
 *     }
 * }
 * 
 * // 3. Factory automatically picks it up (Spring autowiring)
 * // Done! No changes to existing code needed.
 * }
 * </pre>
 * 
 * @see PostVisibility
 * @see VisibilityStrategyFactory
 */
public interface VisibilityStrategy {

    /**
     * Check if a user can view a post based on visibility rules.
     * 
     * Each strategy implements its own logic:
     * - PUBLIC: Always returns true
     * - CONNECTIONS_ONLY: Checks connection status
     * - PRIVATE: Only author can view
     * 
     * @param post The post to check
     * @param userId ID of the user trying to view (can be null for anonymous)
     * @return true if user can view the post
     */
    boolean canView(Post post, Long userId);

    /**
     * Get the visibility level this strategy supports.
     * 
     * Used by VisibilityStrategyFactory to map visibility → strategy.
     * 
     * @return PostVisibility enum value
     */
    PostVisibility getSupportedVisibility();
}

