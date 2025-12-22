package com.linkedin.connection.patterns.strategy;

import com.linkedin.connection.dto.ConnectionSuggestionDto;
import com.linkedin.connection.model.ConnectionState;
import com.linkedin.connection.repository.ConnectionRepository;
import com.linkedin.user.client.UserServiceClient;
import com.linkedin.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Strategy for suggesting connections based on mutual connections.
 * 
 * Algorithm:
 * 1. Get all active connections of the user (user's 1st-degree network)
 * 2. For each connection, get their connections (2nd-degree network)
 * 3. Count how many times each 2nd-degree user appears (mutual connection count)
 * 4. Rank by mutual connection count (more mutual = higher priority)
 * 5. Filter out already connected users
 * 6. Return top N suggestions
 * 
 * Example:
 * <pre>
 * User A is connected to: [B, C, D]
 * B is connected to: [A, E, F]
 * C is connected to: [A, E, G]
 * D is connected to: [A, F, H]
 * 
 * 2nd-degree network of A:
 * E: 2 mutual connections (B and C)
 * F: 2 mutual connections (B and D)
 * G: 1 mutual connection (C)
 * H: 1 mutual connection (D)
 * 
 * Suggestions for A (ranked):
 * 1. E (2 mutual connections) - "You and E have 2 mutual connections"
 * 2. F (2 mutual connections) - "You and F have 2 mutual connections"
 * 3. G (1 mutual connection)
 * 4. H (1 mutual connection)
 * </pre>
 * 
 * Why This Works:
 * - Social proof: If multiple friends know someone, likely relevant
 * - Network clustering: People in same social circles
 * - Trust: Mutual connections provide implicit endorsement
 * 
 * Performance Considerations:
 * - Can be expensive for users with many connections
 * - Should be cached (Redis)
 * - May need pagination for large networks
 * - Consider async computation for very large networks
 * 
 * @see SuggestionStrategy
 * @see ConnectionSuggestionDto
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MutualConnectionStrategy implements SuggestionStrategy {

    private final ConnectionRepository connectionRepository;
    private final UserServiceClient userServiceClient;

    @Override
    public List<ConnectionSuggestionDto> generateSuggestions(Long userId, int limit) {
        log.debug("Generating mutual connection suggestions for user {}", userId);

        // Step 1: Get user's active connections (1st-degree network)
        List<Long> firstDegreeConnections = getActiveConnections(userId);
        
        if (firstDegreeConnections.isEmpty()) {
            log.debug("User {} has no connections, cannot suggest based on mutual connections", userId);
            return Collections.emptyList();
        }

        log.debug("User {} has {} first-degree connections", userId, firstDegreeConnections.size());

        // Step 2: Get 2nd-degree network (connections of connections)
        Map<Long, Integer> mutualConnectionCounts = new HashMap<>();
        
        for (Long connectedUserId : firstDegreeConnections) {
            List<Long> secondDegreeConnections = getActiveConnections(connectedUserId);
            
            for (Long secondDegreeUserId : secondDegreeConnections) {
                // Skip if it's the original user or already connected
                if (secondDegreeUserId.equals(userId) || firstDegreeConnections.contains(secondDegreeUserId)) {
                    continue;
                }
                
                // Count mutual connections
                mutualConnectionCounts.merge(secondDegreeUserId, 1, Integer::sum);
            }
        }

        log.debug("Found {} potential suggestions with mutual connections", mutualConnectionCounts.size());

        // Step 3: Convert to DTOs and rank by mutual connection count
        List<ConnectionSuggestionDto> suggestions = mutualConnectionCounts.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed()) // Sort by count descending
                .limit(limit)
                .map(entry -> buildSuggestionDto(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        log.info("Generated {} mutual connection suggestions for user {}", suggestions.size(), userId);
        return suggestions;
    }

    @Override
    public String getStrategyName() {
        return "Mutual Connections";
    }

    @Override
    public double getStrategyWeight() {
        return 1.0; // Highest weight - mutual connections are strongest signal
    }

    @Override
    public boolean isApplicable(Long userId) {
        // Only applicable if user has at least one connection
        long connectionCount = connectionRepository.countActiveConnectionsByUserId(userId);
        return connectionCount > 0;
    }

    @Override
    public String getDescription() {
        return "Suggests users who are connected to your connections. " +
               "The more mutual connections you have, the higher the suggestion.";
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Gets all active connection user IDs for a given user.
     * Returns a list of user IDs that the given user is connected with.
     * 
     * @param userId The user ID
     * @return List of connected user IDs
     */
    private List<Long> getActiveConnections(Long userId) {
        return connectionRepository.findByUserIdAndState(userId, ConnectionState.ACCEPTED)
                .stream()
                .map(connection -> connection.getOtherUserId(userId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Builds a ConnectionSuggestionDto for a suggested user.
     * Fetches real user details from user-service via Feign.
     * 
     * @param suggestedUserId The user ID to suggest
     * @param mutualCount Number of mutual connections
     * @return ConnectionSuggestionDto with real user data
     */
    private ConnectionSuggestionDto buildSuggestionDto(Long suggestedUserId, Integer mutualCount) {
        try {
            // Fetch real user details from user-service
            UserResponse user = userServiceClient.getUserById(suggestedUserId);
            
            // Calculate suggestion score (0.0 to 1.0)
            // More mutual connections = higher score
            // Using logarithmic scale to avoid extreme scores
            double score = Math.min(1.0, Math.log10(mutualCount + 1) / Math.log10(11)); // Max at 10 mutual
            
            String reason = generateReason(mutualCount);

            return ConnectionSuggestionDto.builder()
                    .userId(user.getId())
                    .name(user.getName())
                    .headline(user.getHeadline())
                    .profilePictureUrl(user.getProfilePictureUrl())
                    .location(user.getLocation())
                    .accountType(user.getAccountType())
                    .mutualConnections(mutualCount)
                    .suggestionReason(reason)
                    .suggestionScore(score)
                    .build();
                    
        } catch (Exception ex) {
            log.warn("Failed to fetch user details for suggestion userId {}: {}", 
                suggestedUserId, ex.getMessage());
            
            // Fallback: Return suggestion with minimal data if user-service is down
            double score = Math.min(1.0, Math.log10(mutualCount + 1) / Math.log10(11));
            String reason = generateReason(mutualCount);
            
            return ConnectionSuggestionDto.builder()
                    .userId(suggestedUserId)
                    .name("User " + suggestedUserId)
                    .headline("Professional")
                    .profilePictureUrl(null)
                    .location(null)
                    .accountType(com.linkedin.user.model.AccountType.BASIC)
                    .mutualConnections(mutualCount)
                    .suggestionReason(reason)
                    .suggestionScore(score)
                    .build();
        }
    }

    /**
     * Generates a human-readable reason for the suggestion.
     * 
     * @param mutualCount Number of mutual connections
     * @return Suggestion reason text
     */
    private String generateReason(Integer mutualCount) {
        if (mutualCount == 1) {
            return "You have 1 mutual connection";
        } else if (mutualCount <= 5) {
            return String.format("You have %d mutual connections", mutualCount);
        } else if (mutualCount <= 10) {
            return String.format("You have %d mutual connections - well connected!", mutualCount);
        } else {
            return String.format("You have %d mutual connections - very well connected!", mutualCount);
        }
    }

    /**
     * Alternative implementation using repository's findMutualConnections.
     * More efficient but requires the other user ID.
     * 
     * This could be used for targeted suggestions (e.g., profile view page).
     * 
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return Number of mutual connections
     */
    public int countMutualConnectionsDirect(Long userId1, Long userId2) {
        List<Long> mutualConnections = connectionRepository.findMutualConnections(userId1, userId2);
        return mutualConnections.size();
    }
}

