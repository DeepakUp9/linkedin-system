package com.linkedin.user.client;

import com.linkedin.user.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client for communicating with the User Service.
 * 
 * Purpose:
 * Provides a declarative REST client to call user-service endpoints.
 * Eliminates the need for manual HTTP client code (RestTemplate, WebClient).
 * 
 * Configuration:
 * - name: "user-service" - Service name for discovery/load balancing
 * - url: "${user.service.url}" - Direct URL (fallback if no service discovery)
 * - configuration: FeignConfig.class - Custom Feign configuration
 * 
 * Service Discovery vs Direct URL:
 * 
 * With Eureka (Service Discovery):
 * {@code @FeignClient(name = "user-service")}
 * → Feign queries Eureka for user-service instances
 * → Load balances across available instances
 * → Automatic failover if instance goes down
 * 
 * Without Eureka (Direct URL):
 * {@code @FeignClient(name = "user-service", url = "${user.service.url}")}
 * → Feign calls http://localhost:8081 (or configured URL)
 * → No service discovery, no load balancing
 * → Simpler for development/testing
 * 
 * How It Works:
 * <pre>
 * // Service layer code
 * UserResponse user = userServiceClient.getUserById(123L);
 * 
 * // Behind the scenes:
 * 1. Feign creates HTTP request: GET http://user-service/api/users/123
 * 2. FeignConfig's RequestInterceptor adds JWT token
 * 3. HTTP call is made to user-service
 * 4. Response JSON is deserialized to UserResponse
 * 5. If error (404, 500, etc.), ErrorDecoder translates to exception
 * </pre>
 * 
 * Example Usage:
 * <pre>
 * {@code
 * @Service
 * public class ConnectionService {
 *     @Autowired
 *     private UserServiceClient userServiceClient;
 *     
 *     public void sendConnectionRequest(Long requesterId, Long addresseeId) {
 *         // 1. Validate users exist
 *         UserResponse requester = userServiceClient.getUserById(requesterId);
 *         UserResponse addressee = userServiceClient.getUserById(addresseeId);
 *         
 *         // 2. Business validation
 *         if (!addressee.canReceiveConnectionRequests()) {
 *             throw new ValidationException("User cannot receive requests");
 *         }
 *         
 *         // 3. Create connection
 *         // ...
 *     }
 * }
 * }
 * </pre>
 * 
 * Error Handling:
 * Errors are translated by FeignConfig's ErrorDecoder:
 * - 404 → ResourceNotFoundException("User not found")
 * - 401/403 → SecurityException("Unauthorized")
 * - 500 → ServiceException("User service unavailable")
 * 
 * Caching:
 * Consider caching user responses to reduce inter-service calls:
 * <pre>
 * {@code
 * @Cacheable(value = "userProfiles", key = "#userId")
 * public UserResponse getUserById(Long userId) {
 *     return userServiceClient.getUserById(userId);
 * }
 * }
 * </pre>
 * 
 * Performance Considerations:
 * - Each Feign call adds network latency (5-50ms)
 * - Batch requests when possible (getUsersByIds)
 * - Use caching for frequently accessed users
 * - Consider async calls for non-critical data
 * 
 * Circuit Breaker Integration (Resilience4j):
 * <pre>
 * {@code
 * @FeignClient(
 *     name = "user-service",
 *     url = "${user.service.url}",
 *     fallback = UserServiceFallback.class
 * )
 * }
 * </pre>
 * 
 * Security:
 * - JWT token is automatically added by FeignConfig's RequestInterceptor
 * - User-service validates the token
 * - Use HTTPS in production
 * 
 * Configuration (application.yml):
 * <pre>
 * user:
 *   service:
 *     url: http://localhost:8081  # User service URL
 * 
 * feign:
 *   client:
 *     config:
 *       user-service:
 *         connectTimeout: 2000   # 2 seconds to establish connection
 *         readTimeout: 5000      # 5 seconds to receive response
 * </pre>
 * 
 * Related:
 * @see com.linkedin.connection.config.FeignConfig
 * @see com.linkedin.user.dto.UserResponse
 * @see org.springframework.cloud.openfeign.FeignClient
 * 
 * @author LinkedIn System
 * @version 1.0
 */
@FeignClient(
    name = "user-service",
    url = "${user.service.url:http://localhost:8081}",
    configuration = com.linkedin.connection.config.FeignConfig.class
)
public interface UserServiceClient {

    /**
     * Retrieves a user by their ID.
     * 
     * Endpoint: GET /api/users/{userId}
     * 
     * Use Cases:
     * - Validate user exists before sending connection request
     * - Fetch user details for connection DTOs
     * - Check if user is active/suspended
     * 
     * Example:
     * <pre>
     * {@code
     * UserResponse user = userServiceClient.getUserById(123L);
     * System.out.println(user.getName()); // "John Doe"
     * }
     * </pre>
     * 
     * Error Cases:
     * - 404: User not found → ResourceNotFoundException
     * - 401: Invalid/expired JWT → SecurityException
     * - 500: User service down → ServiceException
     * 
     * @param userId The ID of the user to retrieve
     * @return UserResponse containing user details
     * @throws com.linkedin.common.exceptions.ResourceNotFoundException if user not found
     * @throws com.linkedin.common.exceptions.SecurityException if unauthorized
     * @throws com.linkedin.common.exceptions.ServiceException if service unavailable
     */
    @GetMapping("/api/users/{userId}")
    UserResponse getUserById(@PathVariable("userId") Long userId);

    /**
     * Retrieves multiple users by their IDs in a single request.
     * 
     * Endpoint: GET /api/users/batch?ids=1,2,3
     * 
     * Use Cases:
     * - Fetch details for all users in a connection list
     * - Get user info for connection suggestions
     * - Populate DTOs for multiple connections
     * 
     * Benefits:
     * - Single network call instead of N calls
     * - Reduces latency (10 users: 1 call vs 10 calls)
     * - More efficient for bulk operations
     * 
     * Example:
     * <pre>
     * {@code
     * List<Long> userIds = Arrays.asList(1L, 2L, 3L);
     * List<UserResponse> users = userServiceClient.getUsersByIds(userIds);
     * 
     * for (UserResponse user : users) {
     *     System.out.println(user.getName());
     * }
     * }
     * </pre>
     * 
     * Performance:
     * - 10 individual calls: ~50-100ms
     * - 1 batch call: ~10-20ms
     * - 5x-10x faster
     * 
     * Limitations:
     * - User-service may limit batch size (e.g., max 100 users)
     * - Returns only found users (missing IDs are skipped)
     * 
     * @param userIds List of user IDs to retrieve
     * @return List of UserResponse objects (may be fewer than requested if some not found)
     */
    @GetMapping("/api/users/batch")
    List<UserResponse> getUsersByIds(@RequestParam("ids") List<Long> userIds);

    /**
     * Checks if a user exists and is active.
     * 
     * Endpoint: GET /api/users/{userId}/exists
     * 
     * Use Cases:
     * - Quick validation without fetching full user details
     * - Pre-check before expensive operations
     * - Bulk user existence validation
     * 
     * Benefits:
     * - Lightweight response (boolean instead of full user object)
     * - Faster than getUserById (no need to serialize full user)
     * - Lower bandwidth usage
     * 
     * Example:
     * <pre>
     * {@code
     * if (!userServiceClient.userExists(addresseeId)) {
     *     throw new ValidationException("Addressee does not exist");
     * }
     * // Proceed with connection request
     * }
     * </pre>
     * 
     * @param userId The ID of the user to check
     * @return true if user exists and is active, false otherwise
     */
    @GetMapping("/api/users/{userId}/exists")
    boolean userExists(@PathVariable("userId") Long userId);

    /**
     * Searches for users by industry.
     * 
     * Endpoint: GET /api/users/search/by-industry?industry=Technology&limit=50
     * 
     * Use Cases:
     * - Connection suggestions (SameIndustryStrategy)
     * - "People in your industry" feature
     * - Network expansion recommendations
     * 
     * Example:
     * <pre>
     * {@code
     * List<UserResponse> techUsers = 
     *     userServiceClient.searchUsersByIndustry("Technology", 50);
     * 
     * // Use for connection suggestions
     * for (UserResponse user : techUsers) {
     *     if (!alreadyConnected(user.getId())) {
     *         suggestions.add(user);
     *     }
     * }
     * }
     * </pre>
     * 
     * @param industry The industry to search for
     * @param limit Maximum number of results
     * @return List of users in the specified industry
     */
    @GetMapping("/api/users/search/by-industry")
    List<UserResponse> searchUsersByIndustry(
        @RequestParam("industry") String industry,
        @RequestParam(value = "limit", defaultValue = "50") int limit
    );

    /**
     * Note: Additional endpoints can be added as needed:
     * 
     * - searchUsersByLocation(String location)
     * - searchUsersByCompany(String company)
     * - getUsersBySkills(List<String> skills)
     * - getRecommendedUsers(Long userId, int limit)
     * 
     * Always consider:
     * - Are we making too many inter-service calls? (use batch endpoints)
     * - Can we cache the response? (use Spring Cache)
     * - Do we need all fields? (consider lighter DTOs for specific use cases)
     */
}

