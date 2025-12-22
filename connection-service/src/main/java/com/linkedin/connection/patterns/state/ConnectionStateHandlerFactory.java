package com.linkedin.connection.patterns.state;

import com.linkedin.connection.model.ConnectionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for retrieving the appropriate ConnectionStateHandler based on state.
 * 
 * Design Pattern: Factory Pattern + State Pattern
 * - Centralizes the creation/retrieval of state handlers
 * - Maps each ConnectionState enum to its handler implementation
 * - Uses EnumMap for O(1) lookup performance
 * 
 * Pattern Structure:
 * <pre>
 * ConnectionStateHandlerFactory
 *         ↓ getHandler(PENDING)
 * Returns: PendingStateHandler
 * 
 * ConnectionStateHandlerFactory
 *         ↓ getHandler(ACCEPTED)
 * Returns: AcceptedStateHandler
 * </pre>
 * 
 * Benefits:
 * 1. **Single Point of Access**: All handler retrieval goes through this factory
 * 2. **Type Safety**: EnumMap ensures all states are covered
 * 3. **Performance**: EnumMap is faster than HashMap for enums
 * 4. **Dependency Injection**: Spring injects all handlers automatically
 * 
 * Usage Example:
 * <pre>
 * {@code
 * @Autowired
 * private ConnectionStateHandlerFactory handlerFactory;
 * 
 * Connection connection = // ... get connection
 * ConnectionStateHandler handler = handlerFactory.getHandler(connection.getState());
 * 
 * if (handler.canAccept(connection)) {
 *     handler.accept(connection);
 * }
 * }
 * </pre>
 * 
 * Spring Integration:
 * - All handlers are Spring beans (@Component)
 * - Factory receives them via constructor injection
 * - Handlers are singletons (thread-safe, stateless)
 * 
 * @see ConnectionStateHandler
 * @see ConnectionState
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConnectionStateHandlerFactory {

    // Injected by Spring - all implementations of ConnectionStateHandler
    private final List<ConnectionStateHandler> handlers;

    // EnumMap for fast O(1) lookup of handlers by state
    private final Map<ConnectionState, ConnectionStateHandler> handlerMap = new EnumMap<>(ConnectionState.class);

    /**
     * Initializes the handler map after Spring injects all handlers.
     * This method is automatically called by Spring after construction.
     * 
     * Process:
     * 1. Spring injects all @Component implementations of ConnectionStateHandler
     * 2. This method loops through them
     * 3. Each handler reports its managed state via getHandledState()
     * 4. We build a map: ConnectionState → Handler
     * 
     * Validation:
     * - Ensures all ConnectionState enums have a handler
     * - Logs warning if any state is missing a handler
     * - Prevents duplicate handlers for the same state
     */
    @jakarta.annotation.PostConstruct
    private void initializeHandlerMap() {
        log.info("Initializing ConnectionStateHandlerFactory with {} handlers", handlers.size());

        // Build map: ConnectionState → ConnectionStateHandler
        for (ConnectionStateHandler handler : handlers) {
            ConnectionState state = handler.getHandledState();
            
            // Check for duplicate handlers
            if (handlerMap.containsKey(state)) {
                log.warn("Duplicate handler detected for state: {}. Using: {}", 
                    state, handler.getClass().getSimpleName());
            }
            
            handlerMap.put(state, handler);
            log.debug("Registered handler for state {}: {}", 
                state, handler.getClass().getSimpleName());
        }

        // Validate all states have handlers
        for (ConnectionState state : ConnectionState.values()) {
            if (!handlerMap.containsKey(state)) {
                log.error("No handler found for state: {}. This is a critical configuration error!", state);
                throw new IllegalStateException("Missing handler for ConnectionState: " + state);
            }
        }

        log.info("Successfully registered handlers for all {} connection states", handlerMap.size());
        handlerMap.forEach((state, handler) -> 
            log.debug("  {} → {}", state, handler.getClass().getSimpleName())
        );
    }

    /**
     * Retrieves the appropriate state handler for the given state.
     * 
     * @param state The connection state
     * @return The handler for that state
     * @throws IllegalArgumentException if no handler exists for the state (should never happen)
     */
    public ConnectionStateHandler getHandler(ConnectionState state) {
        if (state == null) {
            throw new IllegalArgumentException("ConnectionState cannot be null");
        }

        ConnectionStateHandler handler = handlerMap.get(state);
        
        if (handler == null) {
            log.error("No handler found for state: {}. This should not happen after initialization!", state);
            throw new IllegalStateException("No handler configured for state: " + state);
        }

        return handler;
    }

    /**
     * Convenience method to get handler directly from a Connection entity.
     * 
     * @param connection The connection entity
     * @return The handler for the connection's current state
     */
    public ConnectionStateHandler getHandlerForConnection(com.linkedin.connection.model.Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null");
        }
        return getHandler(connection.getState());
    }

    /**
     * Checks if a handler exists for the given state.
     * Primarily for testing and validation.
     * 
     * @param state The connection state
     * @return true if a handler exists, false otherwise
     */
    public boolean hasHandler(ConnectionState state) {
        return state != null && handlerMap.containsKey(state);
    }

    /**
     * Gets all registered handlers.
     * Primarily for testing and diagnostics.
     * 
     * @return Map of all state → handler mappings
     */
    public Map<ConnectionState, ConnectionStateHandler> getAllHandlers() {
        return Map.copyOf(handlerMap); // Return immutable copy
    }
}

