package com.linkedin.notification.config;

import feign.Logger;
import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Configuration for Spring Cloud OpenFeign clients.
 * 
 * Purpose:
 * - Configure Feign client logging
 * - Add JWT token propagation to inter-service calls
 * 
 * @see com.linkedin.notification.client.UserServiceClient
 */
@Configuration
@Slf4j
public class FeignConfig {

    /**
     * Configure Feign logger level.
     * 
     * @return Logger level
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Request interceptor to propagate JWT token.
     * 
     * How It Works:
     * 1. Gets JWT from SecurityContext
     * 2. Adds to Authorization header of Feign requests
     * 3. User-service can validate token
     * 
     * @return Request interceptor
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated() && 
                authentication.getCredentials() instanceof String jwtToken) {
                requestTemplate.header("Authorization", "Bearer " + jwtToken);
                log.debug("Propagating JWT token for Feign request to {}", requestTemplate.url());
            } else {
                log.warn("No JWT token found in SecurityContext for Feign request to {}", requestTemplate.url());
            }
        };
    }
}

