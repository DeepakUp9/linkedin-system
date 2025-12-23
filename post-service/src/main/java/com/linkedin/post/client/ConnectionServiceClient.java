package com.linkedin.post.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign Client for Connection Service.
 * 
 * Purpose:
 * Checks connection status and fetches user connections.
 */
@FeignClient(name = "connection-service", url = "${connection.service.url}")
public interface ConnectionServiceClient {

    @GetMapping("/api/connections/check")
    boolean areConnected(@RequestParam("userId1") Long userId1, 
                        @RequestParam("userId2") Long userId2);

    @GetMapping("/api/connections/user/{userId}/ids")
    List<Long> getConnectionIds(@PathVariable("userId") Long userId);
}

