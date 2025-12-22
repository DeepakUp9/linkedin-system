package com.linkedin.connection.service;

import com.linkedin.connection.dto.ConnectionSuggestionDto;
import com.linkedin.connection.patterns.strategy.SuggestionStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating connection suggestions ("People You May Know").
 * Orchestrates multiple suggestion strategies to provide personalized recommendations.
 * 
 * Design Pattern: Strategy Pattern + Composite Pattern
 * - Uses multiple SuggestionStrategy implementations
 * - Combines results from different strategies
 * - Weights and ranks suggestions
 * 
 * Architecture:
 * <pre>
 * ConnectionSuggestionService
 *         ↓ (uses)
 *    List<SuggestionStrategy>
 *         ├─ MutualConnectionStrategy (weight: 1.0)
 *         ├─ SameIndustryStrategy (weight: 0.6)
 *         ├─ SameLocationStrategy (weight: 0.4)
 *         └─ ... (more strategies)
 * </pre>
 * 
 * Algorithm:
 * 1. For each applicable strategy:
 *    a. Generate suggestions
 *    b. Apply strategy weight to scores
 * 2. Combine suggestions from all strategies
 * 3. Aggregate scores for users suggested by multiple strategies
 * 4. Rank by combined score
 * 5. Return top N suggestions
 * 
 * Example:
 * <pre>
 * User X receives suggestions:
 * 
 * MutualConnectionStrategy suggests:
 * - User A (5 mutual) → score: 0.8 × weight 1.0 = 0.8
 * - User B (3 mutual) → score: 0.6 × weight 1.0 = 0.6
 * 
 * SameIndustryStrategy suggests:
 * - User A (same industry) → score: 0.7 × weight 0.6 = 0.42
 * - User C (same industry) → score: 0.8 × weight 0.6 = 0.48
 * 
 * Combined:
 * - User A: 0.8 + 0.42 = 1.22 (highest - both mutual AND same industry!)
 * - User C: 0.48
 * - User B: 0.6
 * 
 * Final ranking: A, B, C
 * </pre>
 * 
 * Benefits:
 * - Personalized: Different users get different suggestions
 * - Diverse: Multiple signals for relevance
 * - Flexible: Easy to add new strategies
 * - Tunable: Adjust weights based on effectiveness
 * 
 * Performance:
 * - Results are cached in Redis (1 hour TTL)
 * - Async refresh for large networks
 * - Strategies can short-circuit if not applicable
 * 
 * @see SuggestionStrategy
 * @see ConnectionSuggestionDto
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionSuggestionService {

    // Spring automatically injects all beans implementing SuggestionStrategy
    private final List<SuggestionStrategy> suggestionStrategies;

    /**
     * Generates connection suggestions for a user.
     * Combines results from all applicable strategies.
     * 
     * @param userId The user ID to generate suggestions for
     * @param limit Maximum number of suggestions to return
     * @return List of suggested connections, ranked by relevance
     */
    @Cacheable(value = "connectionSuggestions", key = "#userId + '_' + #limit")
    public List<ConnectionSuggestionDto> getConnectionSuggestions(Long userId, int limit) {
        log.info("Generating connection suggestions for user {} (limit: {})", userId, limit);

        // Map to store combined suggestions: userId → suggestion with aggregated score
        Map<Long, ConnectionSuggestionDto> combinedSuggestions = new HashMap<>();

        // Execute each applicable strategy
        for (SuggestionStrategy strategy : suggestionStrategies) {
            if (!strategy.isApplicable(userId)) {
                log.debug("Strategy '{}' not applicable for user {}", strategy.getStrategyName(), userId);
                continue;
            }

            log.debug("Executing strategy: {} (weight: {})", 
                strategy.getStrategyName(), strategy.getStrategyWeight());

            try {
                // Generate suggestions using this strategy
                List<ConnectionSuggestionDto> strategySuggestions = 
                    strategy.generateSuggestions(userId, limit * 2); // Get more, will filter later

                log.debug("Strategy '{}' generated {} suggestions", 
                    strategy.getStrategyName(), strategySuggestions.size());

                // Apply strategy weight and combine
                for (ConnectionSuggestionDto suggestion : strategySuggestions) {
                    combineSuggestion(combinedSuggestions, suggestion, strategy);
                }
            } catch (Exception ex) {
                log.error("Error executing strategy '{}' for user {}: {}", 
                    strategy.getStrategyName(), userId, ex.getMessage(), ex);
                // Continue with other strategies
            }
        }

        // Sort by combined score and limit
        List<ConnectionSuggestionDto> rankedSuggestions = combinedSuggestions.values().stream()
                .sorted(Comparator.comparingDouble(ConnectionSuggestionDto::getSuggestionScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        log.info("Generated {} connection suggestions for user {} from {} strategies", 
            rankedSuggestions.size(), userId, suggestionStrategies.size());

        return rankedSuggestions;
    }

    /**
     * Gets suggestions from a specific strategy.
     * Useful for testing or showing strategy-specific suggestions in UI.
     * 
     * @param userId The user ID
     * @param strategyName Name of the strategy to use
     * @param limit Maximum suggestions
     * @return List of suggestions from that strategy
     */
    public List<ConnectionSuggestionDto> getSuggestionsByStrategy(
            Long userId, String strategyName, int limit) {
        
        log.debug("Getting suggestions for user {} using strategy '{}'", userId, strategyName);

        Optional<SuggestionStrategy> strategy = suggestionStrategies.stream()
                .filter(s -> s.getStrategyName().equalsIgnoreCase(strategyName))
                .findFirst();

        if (strategy.isEmpty()) {
            log.warn("Strategy '{}' not found", strategyName);
            return Collections.emptyList();
        }

        if (!strategy.get().isApplicable(userId)) {
            log.debug("Strategy '{}' not applicable for user {}", strategyName, userId);
            return Collections.emptyList();
        }

        return strategy.get().generateSuggestions(userId, limit);
    }

    /**
     * Gets a list of all available strategies and their applicability for a user.
     * Useful for analytics and debugging.
     * 
     * @param userId The user ID
     * @return Map of strategy name to applicability
     */
    public Map<String, Boolean> getAvailableStrategies(Long userId) {
        return suggestionStrategies.stream()
                .collect(Collectors.toMap(
                    SuggestionStrategy::getStrategyName,
                    strategy -> strategy.isApplicable(userId)
                ));
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Combines a suggestion from a strategy with existing combined suggestions.
     * 
     * Strategy:
     * - If user not yet in combined suggestions: Add with weighted score
     * - If user already suggested: Aggregate scores from multiple strategies
     * 
     * Score Aggregation:
     * - Simple addition (can be changed to other aggregation methods)
     * - Allows users suggested by multiple strategies to rank higher
     * 
     * @param combinedSuggestions The combined suggestions map
     * @param newSuggestion New suggestion from a strategy
     * @param strategy The strategy that generated the suggestion
     */
    private void combineSuggestion(
            Map<Long, ConnectionSuggestionDto> combinedSuggestions,
            ConnectionSuggestionDto newSuggestion,
            SuggestionStrategy strategy) {

        Long suggestedUserId = newSuggestion.getUserId();

        // Apply strategy weight to the score
        double weightedScore = newSuggestion.getSuggestionScore() * strategy.getStrategyWeight();

        if (combinedSuggestions.containsKey(suggestedUserId)) {
            // User already suggested by another strategy
            // Aggregate scores and combine reasons
            ConnectionSuggestionDto existing = combinedSuggestions.get(suggestedUserId);
            
            double newCombinedScore = existing.getSuggestionScore() + weightedScore;
            String combinedReason = combineReasons(
                existing.getSuggestionReason(), 
                newSuggestion.getSuggestionReason()
            );

            // Update the existing suggestion
            existing.setSuggestionScore(Math.min(1.0, newCombinedScore)); // Cap at 1.0
            existing.setSuggestionReason(combinedReason);
            
            log.trace("Combined suggestion for user {}: new score = {}", 
                suggestedUserId, existing.getSuggestionScore());
        } else {
            // First time seeing this user
            newSuggestion.setSuggestionScore(weightedScore);
            combinedSuggestions.put(suggestedUserId, newSuggestion);
            
            log.trace("Added new suggestion for user {}: score = {}", 
                suggestedUserId, weightedScore);
        }
    }

    /**
     * Combines reasons from multiple strategies into a single reason.
     * 
     * Example:
     * - Reason 1: "You have 5 mutual connections"
     * - Reason 2: "Works in the same industry"
     * - Combined: "You have 5 mutual connections and work in the same industry"
     * 
     * @param reason1 First reason
     * @param reason2 Second reason
     * @return Combined reason
     */
    private String combineReasons(String reason1, String reason2) {
        if (reason1 == null || reason1.isEmpty()) {
            return reason2;
        }
        if (reason2 == null || reason2.isEmpty()) {
            return reason1;
        }
        
        // Smart combination: lowercase first character of second reason
        String reason2Lower = reason2.substring(0, 1).toLowerCase() + reason2.substring(1);
        return reason1 + " and " + reason2Lower;
    }

    /**
     * Gets count of active strategies.
     * 
     * @return Number of registered strategies
     */
    public int getStrategyCount() {
        return suggestionStrategies.size();
    }

    /**
     * Gets count of applicable strategies for a user.
     * 
     * @param userId The user ID
     * @return Number of applicable strategies
     */
    public long getApplicableStrategyCount(Long userId) {
        return suggestionStrategies.stream()
                .filter(strategy -> strategy.isApplicable(userId))
                .count();
    }
}

