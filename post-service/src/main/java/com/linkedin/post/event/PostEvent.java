package com.linkedin.post.event;

import com.linkedin.post.model.PostType;
import com.linkedin.post.model.PostVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Base event for post-related Kafka events.
 * 
 * Purpose:
 * Published to Kafka when post operations occur.
 * Consumed by Notification Service and other services.
 * 
 * Event Types:
 * - POST_CREATED: New post created
 * - POST_UPDATED: Post edited
 * - POST_DELETED: Post deleted
 * - POST_LIKED: Someone liked post
 * - POST_COMMENTED: Someone commented on post
 * 
 * Flow:
 * <pre>
 * User creates post
 *   ↓
 * PostService.createPost()
 *   ↓
 * kafkaTemplate.send("post-created", PostEvent)
 *   ↓
 * Notification Service receives event
 *   ↓
 * Creates notifications for mentioned users
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostEvent {
    private String eventType; // POST_CREATED, POST_UPDATED, POST_DELETED
    private Long postId;
    private Long authorId;
    private String content;
    private PostType type;
    private PostVisibility visibility;
    private List<Long> mentionedUserIds;
    private List<String> hashtags;
    private LocalDateTime timestamp;
}

