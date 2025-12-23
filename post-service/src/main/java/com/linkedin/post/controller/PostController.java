package com.linkedin.post.controller;

import com.linkedin.post.config.JwtUtil;
import com.linkedin.post.dto.*;
import com.linkedin.post.service.CommentService;
import com.linkedin.post.service.LikeService;
import com.linkedin.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Post operations.
 * 
 * Endpoints:
 * - POST /api/posts - Create post
 * - GET /api/posts/{id} - Get post by ID
 * - PUT /api/posts/{id} - Update post
 * - DELETE /api/posts/{id} - Delete post
 * - POST /api/posts/{id}/like - Like post
 * - DELETE /api/posts/{id}/like - Unlike post
 * - GET /api/posts/{id}/comments - Get comments
 * - POST /api/posts/{id}/comments - Add comment
 * - GET /api/posts/trending - Get trending posts
 * - GET /api/posts/user/{userId} - Get user's posts
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Posts", description = "Post management APIs")
public class PostController {

    private final PostService postService;
    private final CommentService commentService;
    private final LikeService likeService;
    private final JwtUtil jwtUtil;

    // =========================================================================
    // Post CRUD Operations
    // =========================================================================

    @PostMapping
    @Operation(summary = "Create a new post")
    public ResponseEntity<PostResponseDto> createPost(@Valid @RequestBody CreatePostRequest request) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        log.info("User {} creating post", currentUserId);
        
        PostResponseDto post = postService.createPost(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get post by ID")
    public ResponseEntity<PostResponseDto> getPost(@PathVariable Long id) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        PostResponseDto post = postService.getPostById(id, currentUserId);
        return ResponseEntity.ok(post);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update post")
    public ResponseEntity<PostResponseDto> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePostRequest request) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        PostResponseDto post = postService.updatePost(id, request, currentUserId);
        return ResponseEntity.ok(post);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete post")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        postService.deletePost(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Like Operations
    // =========================================================================

    @PostMapping("/{id}/like")
    @Operation(summary = "Like a post")
    public ResponseEntity<Void> likePost(@PathVariable Long id) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        likeService.likePost(id, currentUserId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/like")
    @Operation(summary = "Unlike a post")
    public ResponseEntity<Void> unlikePost(@PathVariable Long id) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        likeService.unlikePost(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Comment Operations
    // =========================================================================

    @GetMapping("/{id}/comments")
    @Operation(summary = "Get comments on a post")
    public ResponseEntity<Page<CommentResponseDto>> getComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        Page<CommentResponseDto> comments = commentService.getRootComments(id, currentUserId, pageable);
        return ResponseEntity.ok(comments);
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add comment to post")
    public ResponseEntity<CommentResponseDto> addComment(
            @PathVariable Long id,
            @Valid @RequestBody CreateCommentRequest request) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        CommentResponseDto comment = commentService.createComment(request, id, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    // =========================================================================
    // Discovery & Search
    // =========================================================================

    @GetMapping("/trending")
    @Operation(summary = "Get trending posts")
    public ResponseEntity<PageResponseDto<PostResponseDto>> getTrendingPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        var posts = postService.getTrendingPosts(currentUserId, pageable);
        
        PageResponseDto<PostResponseDto> response = PageResponseDto.<PostResponseDto>builder()
                .content(posts)
                .page(page)
                .size(size)
                .totalElements((long) posts.size())
                .build();
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get posts by user")
    public ResponseEntity<Page<PostResponseDto>> getUserPosts(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        Page<PostResponseDto> posts = postService.getPostsByUser(userId, currentUserId, pageable);
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/search")
    @Operation(summary = "Search posts")
    public ResponseEntity<Page<PostResponseDto>> searchPosts(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        Page<PostResponseDto> posts = postService.searchPosts(q, currentUserId, pageable);
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/hashtag/{hashtag}")
    @Operation(summary = "Get posts by hashtag")
    public ResponseEntity<Page<PostResponseDto>> getPostsByHashtag(
            @PathVariable String hashtag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        Page<PostResponseDto> posts = postService.searchByHashtag(hashtag, currentUserId, pageable);
        return ResponseEntity.ok(posts);
    }
}

