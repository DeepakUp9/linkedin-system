package com.linkedin.post.controller;

import com.linkedin.post.config.JwtUtil;
import com.linkedin.post.dto.FeedResponseDto;
import com.linkedin.post.service.FeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Feed operations.
 */
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Feed", description = "User feed APIs")
public class FeedController {

    private final FeedService feedService;
    private final JwtUtil jwtUtil;

    @GetMapping
    @Operation(summary = "Get personalized feed")
    public ResponseEntity<FeedResponseDto> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        FeedResponseDto feed = feedService.getPersonalizedFeed(currentUserId, pageable);
        return ResponseEntity.ok(feed);
    }
}

