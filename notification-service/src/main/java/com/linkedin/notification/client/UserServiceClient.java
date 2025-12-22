package com.linkedin.notification.client;

import com.linkedin.notification.user.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for user-service.
 * 
 * Purpose:
 * - Fetch user details (name, email) from user-service
 * - Used by EmailDeliveryStrategy and ConnectionEventConsumer
 * 
 * Configuration:
 * - name: Service name for discovery
 * - url: Fallback URL for local development
 * - configuration: FeignConfig for JWT propagation
 * 
 * Usage:
 * <pre>
 * {@code
 * @Autowired
 * private UserServiceClient userServiceClient;
 * 
 * UserResponse user = userServiceClient.getUserById(123L);
 * String email = user.getEmail();
 * String name = user.getName();
 * }
 * </pre>
 * 
 * @see UserResponse
 * @see com.linkedin.notification.config.FeignConfig
 */
@FeignClient(
    name = "user-service",
    url = "${user.service.url:http://localhost:8081}",
    configuration = com.linkedin.notification.config.FeignConfig.class
)
public interface UserServiceClient {

    /**
     * Get user by ID.
     * 
     * Endpoint: GET /api/users/{userId}
     * 
     * @param userId User ID
     * @return User details
     */
    @GetMapping("/api/users/{userId}")
    UserResponse getUserById(@PathVariable("userId") Long userId);
}

