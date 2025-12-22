package com.linkedin.notification.strategy;

import com.linkedin.notification.model.NotificationChannel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for selecting appropriate delivery strategy based on notification channel.
 * 
 * Purpose:
 * - Maps notification channels to delivery strategies
 * - Provides centralized strategy selection logic
 * - Ensures type safety at compile time
 * 
 * Design Pattern: Factory Pattern + Strategy Pattern
 * 
 * How It Works:
 * <pre>
 * 1. Spring Boot Startup:
 *    - Spring finds all @Component classes implementing DeliveryStrategy
 *    - Injects them into this factory's constructor
 *    - @PostConstruct initializes strategy map
 * 
 * 2. Runtime:
 *    NotificationService: "I need strategy for EMAIL"
 *         ↓
 *    Factory: strategyMap.get(EMAIL)
 *         ↓
 *    Returns: EmailDeliveryStrategy instance
 *         ↓
 *    NotificationService: strategy.deliver(notification)
 * </pre>
 * 
 * Benefits:
 * 1. Loose Coupling: Service doesn't know specific strategy classes
 * 2. Easy Extension: Add new strategy, Spring auto-registers it
 * 3. Type Safe: Compile-time validation
 * 4. Testable: Can mock strategies
 * 
 * Example Usage:
 * <pre>
 * {@code
 * @Service
 * public class NotificationService {
 *     @Autowired
 *     private DeliveryStrategyFactory strategyFactory;
 *     
 *     public void deliverNotification(Notification notification) {
 *         // Get strategy for notification's channel
 *         DeliveryStrategy strategy = strategyFactory.getStrategy(
 *             notification.getChannel()
 *         );
 *         
 *         // Deliver using that strategy
 *         strategy.deliver(notification);
 *     }
 * }
 * }
 * </pre>
 * 
 * Adding New Strategy:
 * <pre>
 * 1. Create class implementing DeliveryStrategy
 * 2. Annotate with @Component
 * 3. Done! Factory auto-discovers it ✅
 * 
 * No changes to factory needed!
 * </pre>
 * 
 * @see DeliveryStrategy
 * @see InAppDeliveryStrategy
 * @see EmailDeliveryStrategy
 * @see PushDeliveryStrategy
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryStrategyFactory {

    /**
     * All delivery strategies, injected by Spring.
     * 
     * Spring Magic:
     * - Finds all beans implementing DeliveryStrategy
     * - Creates a List<DeliveryStrategy>
     * - Injects via constructor
     * 
     * Example at runtime:
     * strategies = [
     *     InAppDeliveryStrategy,
     *     EmailDeliveryStrategy,
     *     PushDeliveryStrategy
     * ]
     */
    private final List<DeliveryStrategy> strategies;

    /**
     * Map of channel to strategy for fast lookup.
     * 
     * Built in @PostConstruct after Spring injection.
     * 
     * Example:
     * {
     *     IN_APP: InAppDeliveryStrategy,
     *     EMAIL: EmailDeliveryStrategy,
     *     PUSH: PushDeliveryStrategy
     * }
     */
    private Map<NotificationChannel, DeliveryStrategy> strategyMap;

    /**
     * Initialize strategy map after Spring injection.
     * 
     * @PostConstruct Lifecycle:
     * 1. Spring creates bean
     * 2. Spring injects dependencies
     * 3. Spring calls @PostConstruct methods
     * 4. Bean ready to use
     * 
     * Why Not Constructor?
     * - Need to process injected strategies list
     * - Better separation of initialization logic
     * - Clearer logging
     */
    @PostConstruct
    public void init() {
        // Build map: channel → strategy
        strategyMap = strategies.stream()
            .collect(Collectors.toMap(
                DeliveryStrategy::getSupportedChannel,
                Function.identity()
            ));
        
        log.info("DeliveryStrategyFactory initialized with {} strategies: {}", 
            strategies.size(), strategyMap.keySet());
        
        // Log each strategy with priority
        strategies.forEach(strategy -> 
            log.debug("Registered strategy: {} for channel {} with priority {}", 
                strategy.getClass().getSimpleName(),
                strategy.getSupportedChannel(),
                strategy.getPriority())
        );
    }

    /**
     * Get delivery strategy for a specific channel.
     * 
     * Fast Lookup:
     * - O(1) map lookup
     * - No if-else chains
     * - Type safe
     * 
     * Error Handling:
     * - Returns null if no strategy for channel
     * - Caller should handle null case
     * - Could throw exception instead (design choice)
     * 
     * Usage:
     * <pre>
     * {@code
     * DeliveryStrategy strategy = factory.getStrategy(NotificationChannel.EMAIL);
     * if (strategy != null) {
     *     strategy.deliver(notification);
     * } else {
     *     log.error("No strategy for channel: {}", channel);
     * }
     * }
     * </pre>
     * 
     * @param channel The notification channel
     * @return Delivery strategy for that channel, or null if not found
     */
    public DeliveryStrategy getStrategy(NotificationChannel channel) {
        DeliveryStrategy strategy = strategyMap.get(channel);
        
        if (strategy == null) {
            log.warn("No delivery strategy found for channel: {}", channel);
        }
        
        return strategy;
    }

    /**
     * Get all registered strategies.
     * 
     * Use Cases:
     * - Testing: Verify all strategies registered
     * - Admin UI: Display available delivery methods
     * - Monitoring: Check strategy health
     * 
     * @return Immutable list of all strategies
     */
    public List<DeliveryStrategy> getAllStrategies() {
        return List.copyOf(strategies);
    }

    /**
     * Get all strategies sorted by priority.
     * 
     * Use Case:
     * When multiple channels enabled, deliver in priority order
     * 
     * Example:
     * <pre>
     * {@code
     * List<DeliveryStrategy> sortedStrategies = factory.getStrategiesByPriority();
     * // Returns: [InAppDeliveryStrategy(1), PushDeliveryStrategy(2), EmailDeliveryStrategy(3)]
     * 
     * for (DeliveryStrategy strategy : sortedStrategies) {
     *     if (isChannelEnabled(strategy.getSupportedChannel())) {
     *         strategy.deliver(notification);
     *     }
     * }
     * }
     * </pre>
     * 
     * @return List of strategies sorted by priority (lowest number first)
     */
    public List<DeliveryStrategy> getStrategiesByPriority() {
        return strategies.stream()
            .sorted((s1, s2) -> Integer.compare(s1.getPriority(), s2.getPriority()))
            .toList();
    }

    /**
     * Check if a strategy exists for given channel.
     * 
     * Use Case:
     * Validate channel before attempting delivery
     * 
     * @param channel The notification channel
     * @return true if strategy exists
     */
    public boolean hasStrategy(NotificationChannel channel) {
        return strategyMap.containsKey(channel);
    }

    /**
     * Get strategy count (for monitoring/health checks).
     * 
     * @return Number of registered strategies
     */
    public int getStrategyCount() {
        return strategies.size();
    }
}

