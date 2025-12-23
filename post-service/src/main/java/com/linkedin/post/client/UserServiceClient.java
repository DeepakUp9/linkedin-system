package com.linkedin.post.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign Client for User Service.
 * 
 * Purpose:
 * Fetches user data from User Service for enrichment.
 */
@FeignClient(name = "user-service", url = "${user.service.url}")
public interface UserServiceClient {

    @GetMapping("/api/users/{id}")
    UserResponse getUserById(@PathVariable("id") Long id);
}

