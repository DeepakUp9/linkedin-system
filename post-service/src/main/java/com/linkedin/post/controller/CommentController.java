package com.linkedin.post.controller;

import com.linkedin.post.config.JwtUtil;
import com.linkedin.post.dto.CommentResponseDto;
import com.linkedin.post.dto.UpdateCommentRequest;
import com.linkedin.post.service.CommentService;
import com.linkedin.post.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Comment operations.
 */
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Comments", description = "Comment management APIs")
public class CommentController {

    private final CommentService commentService;
    private final LikeService likeService;
    private final JwtUtil jwtUtil;

    @GetMapping("/{id}")
    @Operation(summary = "Get comment by ID")
    public ResponseEntity<CommentResponseDto> getComment(@PathVariable Long id) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        CommentResponseDto comment = commentService.getCommentById(id, currentUserId);
        return ResponseEntity.ok(comment);
    }

    @GetMapping("/{id}/replies")
    @Operation(summary = "Get replies to a comment")
    public ResponseEntity<Page<CommentResponseDto>> getReplies(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        Page<CommentResponseDto> replies = commentService.getReplies(id, currentUserId, pageable);
        return ResponseEntity.ok(replies);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update comment")
    public ResponseEntity<CommentResponseDto> updateComment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommentRequest request) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        CommentResponseDto comment = commentService.updateComment(id, request, currentUserId);
        return ResponseEntity.ok(comment);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete comment")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        commentService.deleteComment(id, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "Like a comment")
    public ResponseEntity<Void> likeComment(
            @PathVariable Long id,
            @RequestParam Long postId) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        likeService.likeComment(postId, id, currentUserId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/like")
    @Operation(summary = "Unlike a comment")
    public ResponseEntity<Void> unlikeComment(
            @PathVariable Long id,
            @RequestParam Long postId) {
        Long currentUserId = jwtUtil.getCurrentUserId();
        likeService.unlikeComment(postId, id, currentUserId);
        return ResponseEntity.noContent().build();
    }
}

