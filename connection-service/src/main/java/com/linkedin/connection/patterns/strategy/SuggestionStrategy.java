package com.linkedin.connection.patterns.strategy;

import com.linkedin.connection.dto.ConnectionSuggestionDto;

import java.util.List;

/**
 * Strategy Pattern Interface for generating connection suggestions.
 * 
 * Design Pattern: Strategy Pattern
 * - Defines a family of algorithms (suggestion strategies)
 * - Encapsulates each algorithm
 * - Makes them interchangeable
 * 
 * Use Case: "People You May Know" Feature
 * Different algorithms for suggesting connections:
 * - Mutual connections: Suggest users with many mutual connections
 * - Same industry: Suggest users in the same industry
 * - Same location: Suggest users in the same city
 * - Same company: Suggest current/past colleagues
 * - Similar interests: Based on profile keywords, skills
 * 
 * Pattern Structure:
 * <pre>
 * SuggestionStrategy (interface)
 *      ↑
 *      ├── MutualConnectionStrategy
 *      ├── SameIndustryStrategy
 *      ├── SameLocationStrategy
 *      └── SameCompanyStrategy
 * </pre>
 * 
 * Benefits:
 * 1. Open/Closed Principle: Add new strategies without changing existing code
 * 2. Single Responsibility: Each strategy has one algorithm
 * 3. Testability: Easy to test each strategy independently
 * 4. Flexibility: Mix and match strategies, adjust weights
 * 
 * Usage Example:
 * <pre>
 * {@code
 * List<SuggestionStrategy> strategies = List.of(
 *     new MutualConnectionStrategy(),
 *     new SameIndustryStrategy()
 * );
 * 
 * for (SuggestionStrategy strategy : strategies) {
 *     List<ConnectionSuggestionDto> suggestions = 
 *         strategy.generateSuggestions(userId, limit);
 *     // Combine and rank suggestions
 * }
 * }
 * </pre>
 * 
 * Real-World Analogy:
 * Like different recommendation algorithms on Netflix:
 * - "Because you watched X"
 * - "Popular in your region"
 * - "Trending now"
 * Each is a different strategy to suggest content.
 * 
 * @see ConnectionSuggestionDto
 * @see com.linkedin.connection.service.ConnectionSuggestionService
 */
public interface SuggestionStrategy {

    /**
     * Generates connection suggestions for a user using this strategy's algorithm.
     * 
     * @param userId The user ID to generate suggestions for
     * @param limit Maximum number of suggestions to return
     * @return List of suggested connections with scores and reasons
     */
    List<ConnectionSuggestionDto> generateSuggestions(Long userId, int limit);

    /**
     * Returns the name of this strategy.
     * Used for logging, debugging, and analytics.
     * 
     * @return Strategy name (e.g., "Mutual Connections", "Same Industry")
     */
    String getStrategyName();

    /**
     * Returns the weight/priority of this strategy.
     * Higher weight = more important in combined scoring.
     * 
     * Range: 0.0 to 1.0
     * - 1.0: Highest priority (e.g., mutual connections)
     * - 0.5: Medium priority (e.g., same location)
     * - 0.3: Low priority (e.g., same school long ago)
     * 
     * @return Weight between 0.0 and 1.0
     */
    double getStrategyWeight();

    /**
     * Checks if this strategy can generate suggestions for the given user.
     * 
     * Examples:
     * - MutualConnectionStrategy: Only if user has existing connections
     * - SameCompanyStrategy: Only if user has company info in profile
     * 
     * @param userId The user ID to check
     * @return true if strategy is applicable, false otherwise
     */
    boolean isApplicable(Long userId);

    /**
     * Returns a description of how this strategy works.
     * Used for transparency in UI ("Why am I seeing this suggestion?")
     * 
     * @return Human-readable description
     */
    default String getDescription() {
        return "Suggests connections based on " + getStrategyName();
    }
}

