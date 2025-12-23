package com.linkedin.post.strategy;

import com.linkedin.common.exceptions.ResourceNotFoundException;
import com.linkedin.post.model.PostVisibility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for selecting the appropriate visibility strategy.
 * 
 * Purpose:
 * Maps PostVisibility enum → VisibilityStrategy implementation.
 * 
 * Design Pattern: Factory Pattern + Strategy Pattern
 * - Factory: Creates/selects strategies
 * - Strategy: Encapsulates visibility algorithms
 * 
 * How It Works:
 * 1. Spring injects all VisibilityStrategy implementations
 * 2. Constructor builds a map: PostVisibility → Strategy
 * 3. getStrategy() looks up the correct strategy
 * 
 * Architecture:
 * <pre>
 * VisibilityStrategyFactory
 *   ├─ strategies: Map<PostVisibility, VisibilityStrategy>
 *   │    ├─ PUBLIC → PublicVisibilityStrategy
 *   │    ├─ CONNECTIONS_ONLY → ConnectionsOnlyVisibilityStrategy
 *   │    └─ PRIVATE → PrivateVisibilityStrategy
 *   └─ getStrategy(visibility) → Returns correct strategy
 * </pre>
 * 
 * Spring Magic (Dependency Injection):
 * <pre>
 * {@code
 * @Component
 * public class VisibilityStrategyFactory {
 *     // Spring automatically injects ALL implementations of VisibilityStrategy
 *     public VisibilityStrategyFactory(List<VisibilityStrategy> strategies) {
 *         // strategies list contains:
 *         // [PublicVisibilityStrategy, ConnectionsOnlyVisibilityStrategy, PrivateVisibilityStrategy]
 *     }
 * }
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
 *         // Get strategy for this post's visibility
 *         VisibilityStrategy strategy = strategyFactory.getStrategy(post.getVisibility());
 *         
 *         // Use strategy to check access
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
 * Extensibility:
 * Adding a new visibility level is easy:
 * 
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
 * // 3. Done! Factory automatically picks it up via Spring DI
 * // No changes to existing code needed!
 * }
 * </pre>
 * 
 * @see VisibilityStrategy
 * @see PostVisibility
 */
@Component
@Slf4j
public class VisibilityStrategyFactory {

    /**
     * Map of PostVisibility → VisibilityStrategy.
     * 
     * Built at construction time from all VisibilityStrategy beans.
     * 
     * Example:
     * {
     *   PUBLIC → PublicVisibilityStrategy instance,
     *   CONNECTIONS_ONLY → ConnectionsOnlyVisibilityStrategy instance,
     *   PRIVATE → PrivateVisibilityStrategy instance
     * }
     */
    private final Map<PostVisibility, VisibilityStrategy> strategies;

    /**
     * Constructor: Builds strategy map from injected strategies.
     * 
     * Spring automatically injects ALL implementations of VisibilityStrategy.
     * 
     * Process:
     * 1. Spring finds all @Component classes implementing VisibilityStrategy
     * 2. Creates instances of each
     * 3. Injects them as a List
     * 4. We convert List → Map for O(1) lookup
     * 
     * @param strategyList List of all VisibilityStrategy implementations (injected by Spring)
     */
    public VisibilityStrategyFactory(List<VisibilityStrategy> strategyList) {
        log.info("Initializing VisibilityStrategyFactory with {} strategies", strategyList.size());
        
        // Convert List<VisibilityStrategy> → Map<PostVisibility, VisibilityStrategy>
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        VisibilityStrategy::getSupportedVisibility,  // Key: PostVisibility enum
                        Function.identity()                           // Value: Strategy instance
                ));

        // Log registered strategies
        strategies.forEach((visibility, strategy) -> 
            log.info("Registered strategy for {}: {}", 
                    visibility, 
                    strategy.getClass().getSimpleName())
        );

        // Validate all visibility levels have strategies
        for (PostVisibility visibility : PostVisibility.values()) {
            if (!strategies.containsKey(visibility)) {
                log.error("No strategy found for visibility level: {}", visibility);
                throw new IllegalStateException(
                    "Missing visibility strategy for: " + visibility
                );
            }
        }

        log.info("VisibilityStrategyFactory initialized successfully");
    }

    /**
     * Get the appropriate visibility strategy for a post visibility level.
     * 
     * Lookup is O(1) using HashMap.
     * 
     * @param visibility PostVisibility enum value
     * @return Corresponding VisibilityStrategy implementation
     * @throws ResourceNotFoundException if no strategy found (should never happen if validated in constructor)
     */
    public VisibilityStrategy getStrategy(PostVisibility visibility) {
        log.debug("Getting strategy for visibility: {}", visibility);
        
        VisibilityStrategy strategy = strategies.get(visibility);
        
        if (strategy == null) {
            log.error("No strategy found for visibility: {}", visibility);
            throw new ResourceNotFoundException(
                "No visibility strategy found for: " + visibility
            );
        }
        
        return strategy;
    }

    /**
     * Check if a strategy exists for a visibility level.
     * 
     * @param visibility PostVisibility to check
     * @return true if strategy exists
     */
    public boolean hasStrategy(PostVisibility visibility) {
        return strategies.containsKey(visibility);
    }

    /**
     * Get all registered visibility levels.
     * 
     * @return Set of PostVisibility values that have strategies
     */
    public java.util.Set<PostVisibility> getSupportedVisibilities() {
        return strategies.keySet();
    }
}

