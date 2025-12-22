package com.linkedin.user.patterns.strategy;

import com.linkedin.user.model.AccountType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for selecting the appropriate ProfileValidationStrategy.
 * 
 * This class is the CONTEXT in the Strategy Pattern. It:
 * 1. Holds references to all available validation strategies
 * 2. Maps each AccountType to its corresponding strategy
 * 3. Provides a clean API for retrieving the correct strategy
 * 
 * Design Pattern: Strategy Pattern (Context + Factory)
 * 
 * Pattern Structure:
 * 
 * <pre>
 * {@code
 *     UserService (Client)
 *          │
 *          │ calls getStrategy()
 *          ▼
 *     ValidationStrategyFactory (Context/Factory)
 *          │
 *          │ returns appropriate strategy
 *          ▼
 *     ProfileValidationStrategy (Interface)
 *          │
 *          ├─► BasicProfileValidationStrategy
 *          └─► PremiumProfileValidationStrategy
 * }
 * </pre>
 * 
 * Why Use a Factory Here?
 * 
 * 1. Centralized Strategy Selection:
 *    - Single place to manage AccountType → Strategy mapping
 *    - Services don't need if-else chains
 *    - Easy to add new strategies (just add to map)
 * 
 * 2. Open/Closed Principle:
 *    - Open for extension: Add EnterpriseProfileValidationStrategy
 *    - Closed for modification: Don't change existing strategies
 * 
 * 3. Dependency Inversion:
 *    - Services depend on ProfileValidationStrategy interface
 *    - Don't depend on concrete strategy implementations
 * 
 * 4. Type Safety:
 *    - EnumMap ensures all AccountTypes have a strategy
 *    - Compile-time checking of strategy completeness
 * 
 * Without Factory (Bad Approach):
 * 
 * <pre>
 * {@code
 * @Service
 * public class UserService {
 *     @Autowired private BasicProfileValidationStrategy basicStrategy;
 *     @Autowired private PremiumProfileValidationStrategy premiumStrategy;
 *     
 *     public void validateUser(User user) {
 *         // ❌ Service has to know about all strategies
 *         // ❌ Adding new strategy requires modifying this code
 *         if (user.getAccountType() == AccountType.BASIC) {
 *             basicStrategy.validate(user);
 *         } else if (user.getAccountType() == AccountType.PREMIUM) {
 *             premiumStrategy.validate(user);
 *         }
 *         // What about ENTERPRISE? Need to modify this method!
 *     }
 * }
 * }
 * </pre>
 * 
 * With Factory (Good Approach):
 * 
 * <pre>
 * {@code
 * @Service
 * public class UserService {
 *     @Autowired private ValidationStrategyFactory strategyFactory;
 *     
 *     public void validateUser(User user) {
 *         // ✅ Service doesn't know about concrete strategies
 *         // ✅ Adding new strategy doesn't change this code
 *         ProfileValidationStrategy strategy = 
 *             strategyFactory.getStrategy(user.getAccountType());
 *         strategy.validate(user);
 *     }
 * }
 * }
 * </pre>
 * 
 * Implementation Details:
 * 
 * 1. EnumMap for Performance:
 *    - O(1) lookup time (array-based internally)
 *    - More memory efficient than HashMap
 *    - Type-safe (only AccountType keys allowed)
 * 
 * 2. Constructor Injection:
 *    - Spring auto-wires all ProfileValidationStrategy beans
 *    - Fails fast if strategy is missing at startup
 *    - Immutable after construction (thread-safe)
 * 
 * 3. Automatic Strategy Registration:
 *    - Each strategy knows its AccountType (getAccountType())
 *    - Factory automatically builds map from list of strategies
 *    - No manual mapping needed
 * 
 * 4. Validation at Startup:
 *    - Ensures all AccountType values have strategies
 *    - Throws exception if any strategy is missing
 *    - Fail-fast principle (better than runtime NPE)
 * 
 * Thread Safety:
 * - EnumMap is populated in constructor (before sharing)
 * - Map is read-only after construction
 * - Multiple threads can safely call getStrategy() concurrently
 * 
 * Future Extensions:
 * - Add TrialProfileValidationStrategy for trial accounts
 * - Add EnterpriseProfileValidationStrategy for enterprise accounts
 * - Add StudentProfileValidationStrategy with .edu email requirement
 * - Just implement ProfileValidationStrategy, Spring will auto-wire it!
 * 
 * @see ProfileValidationStrategy
 * @see BasicProfileValidationStrategy
 * @see PremiumProfileValidationStrategy
 */
@Component
@Slf4j
public class ValidationStrategyFactory {

    /**
     * Map of AccountType to corresponding validation strategy.
     * 
     * EnumMap is used for:
     * - Performance: O(1) lookup, array-based internally
     * - Memory efficiency: More compact than HashMap
     * - Type safety: Only AccountType keys allowed
     * 
     * Populated in constructor via dependency injection.
     * Immutable after construction (thread-safe).
     */
    private final Map<AccountType, ProfileValidationStrategy> strategies;

    /**
     * Constructor with dependency injection.
     * 
     * Spring automatically finds all beans that implement ProfileValidationStrategy
     * and injects them as a list. This constructor then:
     * 1. Creates an EnumMap to store AccountType → Strategy mappings
     * 2. Iterates through each strategy
     * 3. Registers each strategy using its getAccountType() method
     * 4. Validates that all AccountType values have strategies
     * 
     * Process:
     * 1. Spring scans for @Component classes
     * 2. Finds BasicProfileValidationStrategy, PremiumProfileValidationStrategy
     * 3. Creates singleton instances of each
     * 4. Injects them as List<ProfileValidationStrategy>
     * 5. This constructor builds the EnumMap
     * 6. Validates completeness
     * 
     * Fail-Fast:
     * - If any AccountType is missing a strategy, throws IllegalStateException
     * - Better to fail at startup than get NullPointerException at runtime
     * 
     * Example Flow:
     * <pre>
     * {@code
     * // Spring creates:
     * BasicProfileValidationStrategy basicStrategy = new BasicProfileValidationStrategy();
     * PremiumProfileValidationStrategy premiumStrategy = new PremiumProfileValidationStrategy();
     * 
     * // Spring injects:
     * List<ProfileValidationStrategy> allStrategies = 
     *     List.of(basicStrategy, premiumStrategy);
     * 
     * // Constructor builds map:
     * strategies.put(AccountType.BASIC, basicStrategy);
     * strategies.put(AccountType.PREMIUM, premiumStrategy);
     * 
     * // Validates:
     * if (strategies.size() != AccountType.values().length) {
     *     throw new IllegalStateException("Missing strategy!");
     * }
     * }
     * </pre>
     * 
     * @param strategyList List of all ProfileValidationStrategy beans (auto-injected by Spring)
     * @throws IllegalStateException if any AccountType is missing a strategy
     */
    public ValidationStrategyFactory(List<ProfileValidationStrategy> strategyList) {
        log.info("Initializing ValidationStrategyFactory with {} strategies", strategyList.size());
        
        // Create EnumMap for efficient AccountType → Strategy lookup
        this.strategies = new EnumMap<>(AccountType.class);
        
        // Register each strategy by its AccountType
        for (ProfileValidationStrategy strategy : strategyList) {
            AccountType accountType = strategy.getAccountType();
            strategies.put(accountType, strategy);
            log.info("Registered validation strategy: {} → {}", 
                accountType, 
                strategy.getClass().getSimpleName()
            );
        }
        
        // Validate that all AccountTypes have strategies
        validateStrategyCompleteness();
        
        log.info("ValidationStrategyFactory initialized successfully with {} strategies", 
            strategies.size());
    }

    /**
     * Gets the appropriate validation strategy for the given account type.
     * 
     * This is the main method that services will call to get the right strategy.
     * 
     * Usage Example:
     * <pre>
     * {@code
     * @Service
     * public class UserService {
     *     @Autowired
     *     private ValidationStrategyFactory strategyFactory;
     *     
     *     public void createUser(CreateUserRequest request) {
     *         // Create user entity
     *         User user = userFactory.createUser(request);
     *         
     *         // Get the right validation strategy
     *         ProfileValidationStrategy strategy = 
     *             strategyFactory.getStrategy(user.getAccountType());
     *         
     *         // Validate using the strategy
     *         strategy.validate(user);
     *         
     *         // Save if valid
     *         userRepository.save(user);
     *     }
     * }
     * }
     * </pre>
     * 
     * Flow:
     * 1. Service calls getStrategy(AccountType.PREMIUM)
     * 2. Factory looks up in EnumMap: strategies.get(AccountType.PREMIUM)
     * 3. Returns PremiumProfileValidationStrategy instance
     * 4. Service calls strategy.validate(user)
     * 5. Premium validation rules are applied
     * 
     * Performance:
     * - O(1) lookup time (EnumMap is array-based)
     * - No if-else chains
     * - No reflection
     * - Thread-safe (map is immutable after construction)
     * 
     * @param accountType Account type to get strategy for (BASIC, PREMIUM, etc.)
     * @return Validation strategy for the given account type
     * @throws IllegalArgumentException if accountType is null
     * @throws IllegalStateException if no strategy found (shouldn't happen after validation)
     */
    public ProfileValidationStrategy getStrategy(AccountType accountType) {
        // Validate input
        if (accountType == null) {
            log.error("Cannot get validation strategy for null AccountType");
            throw new IllegalArgumentException("AccountType cannot be null");
        }
        
        // Lookup strategy in map
        ProfileValidationStrategy strategy = strategies.get(accountType);
        
        // This should never happen due to startup validation
        // But we check anyway for defensive programming
        if (strategy == null) {
            log.error("No validation strategy found for AccountType: {}", accountType);
            throw new IllegalStateException(
                "No validation strategy registered for AccountType: " + accountType
            );
        }
        
        log.debug("Retrieved validation strategy for {}: {}", 
            accountType, 
            strategy.getClass().getSimpleName()
        );
        
        return strategy;
    }

    /**
     * Gets all registered strategies (for testing/debugging).
     * 
     * Useful for:
     * - Unit tests to verify all strategies are registered
     * - Admin endpoints to show available validation strategies
     * - Debugging configuration issues
     * 
     * @return Unmodifiable map of all registered strategies
     */
    public Map<AccountType, ProfileValidationStrategy> getAllStrategies() {
        return Map.copyOf(strategies); // Return immutable copy for safety
    }

    /**
     * Gets the number of registered strategies.
     * 
     * @return Count of registered strategies
     */
    public int getStrategyCount() {
        return strategies.size();
    }

    // ============================================
    // Private Helper Methods
    // ============================================

    /**
     * Validates that all AccountType enum values have corresponding strategies.
     * 
     * This ensures completeness at startup. If a developer adds a new AccountType
     * (e.g., ENTERPRISE) but forgets to implement its validation strategy,
     * this method will throw an exception during application startup.
     * 
     * Fail-Fast Principle:
     * - Better to fail at startup than runtime
     * - Catches configuration errors early
     * - Prevents NullPointerExceptions in production
     * 
     * Validation Logic:
     * 1. Get all AccountType enum values
     * 2. Check if each has a strategy in the map
     * 3. If any is missing, throw IllegalStateException
     * 
     * Example Error:
     * "Validation strategy not found for AccountType: ENTERPRISE. 
     *  Available strategies: [BASIC, PREMIUM]"
     * 
     * @throws IllegalStateException if any AccountType is missing a strategy
     */
    private void validateStrategyCompleteness() {
        log.debug("Validating strategy completeness...");
        
        AccountType[] allAccountTypes = AccountType.values();
        
        // Check each AccountType has a strategy
        for (AccountType accountType : allAccountTypes) {
            if (!strategies.containsKey(accountType)) {
                String errorMessage = String.format(
                    "Validation strategy not found for AccountType: %s. " +
                    "Available strategies: %s. " +
                    "Please implement ProfileValidationStrategy for this account type.",
                    accountType,
                    strategies.keySet()
                );
                log.error(errorMessage);
                throw new IllegalStateException(errorMessage);
            }
        }
        
        log.debug("Strategy completeness validation passed. All {} AccountTypes have strategies.",
            allAccountTypes.length);
    }
}

