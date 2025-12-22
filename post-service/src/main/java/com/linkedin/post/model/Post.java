package com.linkedin.post.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JPA Entity representing a post on LinkedIn.
 * 
 * Purpose:
 * Core entity for all user-generated content including:
 * - Status updates
 * - Image posts
 * - Articles
 * - Shared content
 * 
 * Features:
 * - Rich text support (hashtags, mentions)
 * - Multiple images
 * - Visibility control
 * - Engagement tracking (likes, comments, shares)
 * - Soft delete support
 * 
 * Business Rules:
 * 1. Author cannot like their own post
 * 2. Deleted posts cannot be edited or interacted with
 * 3. Private posts only visible to author
 * 4. Connections_only posts require connection check
 * 
 * Example Usage:
 * <pre>
 * {@code
 * Post post = Post.builder()
 *     .authorId(123L)
 *     .content("Excited to share my new role at Google! #NewJob #Excited")
 *     .type(PostType.TEXT)
 *     .visibility(PostVisibility.PUBLIC)
 *     .build();
 * 
 * postRepository.save(post);
 * }
 * </pre>
 * 
 * @see PostType
 * @see PostVisibility
 */
@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_posts_author", columnList = "author_id"),
    @Index(name = "idx_posts_created", columnList = "created_at"),
    @Index(name = "idx_posts_visibility", columnList = "visibility"),
    @Index(name = "idx_posts_deleted", columnList = "is_deleted"),
    @Index(name = "idx_posts_author_deleted", columnList = "author_id, is_deleted")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"imageUrls", "hashtags", "mentions"})
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID of the user who created this post.
     */
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /**
     * Type of post (TEXT, IMAGE, ARTICLE, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @Builder.Default
    private PostType type = PostType.TEXT;

    /**
     * Main text content of the post.
     * Supports markdown for rich text.
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Visibility setting for this post.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    @Builder.Default
    private PostVisibility visibility = PostVisibility.CONNECTIONS_ONLY;

    /**
     * URLs of images attached to this post (for IMAGE type).
     * Stored as comma-separated string in database.
     * Converted to/from List in application code.
     */
    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    /**
     * ID of the original post if this is a share/repost.
     * Null for original posts.
     */
    @Column(name = "shared_post_id")
    private Long sharedPostId;

    /**
     * Additional text added when sharing someone else's post.
     */
    @Column(name = "share_comment", columnDefinition = "TEXT")
    private String shareComment;

    /**
     * Hashtags extracted from content.
     * Stored as comma-separated string in database.
     * Example: "NewJob,Excited,Google"
     */
    @Column(name = "hashtags", length = 500)
    private String hashtags;

    /**
     * User IDs mentioned in the post (@mentions).
     * Stored as comma-separated string.
     * Example: "123,456,789"
     */
    @Column(name = "mentions", length = 500)
    private String mentions;

    // =========================================================================
    // Engagement Metrics (Denormalized for Performance)
    // =========================================================================

    /**
     * Number of likes on this post.
     * Updated when likes are added/removed.
     */
    @Column(name = "likes_count", nullable = false)
    @Builder.Default
    private Integer likesCount = 0;

    /**
     * Number of comments on this post.
     * Updated when comments are added/deleted.
     */
    @Column(name = "comments_count", nullable = false)
    @Builder.Default
    private Integer commentsCount = 0;

    /**
     * Number of times this post has been shared.
     * Updated when post is shared.
     */
    @Column(name = "shares_count", nullable = false)
    @Builder.Default
    private Integer sharesCount = 0;

    /**
     * Number of views (optional - for analytics).
     */
    @Column(name = "views_count", nullable = false)
    @Builder.Default
    private Integer viewsCount = 0;

    // =========================================================================
    // Soft Delete Support
    // =========================================================================

    /**
     * Whether this post has been deleted (soft delete).
     * Deleted posts are hidden but kept for audit purposes.
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /**
     * When the post was deleted.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // =========================================================================
    // Audit Fields
    // =========================================================================

    /**
     * When the post was created (auto-populated).
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When the post was last updated (auto-populated).
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // =========================================================================
    // Business Logic Methods
    // =========================================================================

    /**
     * Check if this post can be viewed by a specific user.
     * 
     * Visibility Rules:
     * 1. PUBLIC: Everyone can see
     * 2. CONNECTIONS_ONLY: Only connections of author
     * 3. PRIVATE: Only author
     * 
     * @param viewerId ID of the user trying to view
     * @param isConnection Whether viewer is connected to author
     * @return true if post is visible to viewer
     */
    public boolean isVisibleTo(Long viewerId, boolean isConnection) {
        // Deleted posts not visible
        if (deleted) {
            return false;
        }

        // Author can always see their own post
        if (viewerId.equals(authorId)) {
            return true;
        }

        // Check visibility level
        return switch (visibility) {
            case PUBLIC -> true;
            case CONNECTIONS_ONLY -> isConnection;
            case PRIVATE -> false;
        };
    }

    /**
     * Check if a user can edit this post.
     * Only author can edit, and post must not be deleted.
     * 
     * @param userId ID of the user trying to edit
     * @return true if user can edit
     */
    public boolean canBeEditedBy(Long userId) {
        return !deleted && userId.equals(authorId);
    }

    /**
     * Check if a user can delete this post.
     * Only author can delete, and post must not already be deleted.
     * 
     * @param userId ID of the user trying to delete
     * @return true if user can delete
     */
    public boolean canBeDeletedBy(Long userId) {
        return !deleted && userId.equals(authorId);
    }

    /**
     * Check if this is a shared post (repost).
     * 
     * @return true if this is a share
     */
    public boolean isSharedPost() {
        return sharedPostId != null;
    }

    /**
     * Check if this is an original post.
     * 
     * @return true if not a share
     */
    public boolean isOriginalPost() {
        return sharedPostId == null;
    }

    /**
     * Soft delete this post.
     */
    public void markAsDeleted() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * Increment likes count.
     */
    public void incrementLikes() {
        this.likesCount++;
    }

    /**
     * Decrement likes count (cannot go below 0).
     */
    public void decrementLikes() {
        if (this.likesCount > 0) {
            this.likesCount--;
        }
    }

    /**
     * Increment comments count.
     */
    public void incrementComments() {
        this.commentsCount++;
    }

    /**
     * Decrement comments count (cannot go below 0).
     */
    public void decrementComments() {
        if (this.commentsCount > 0) {
            this.commentsCount--;
        }
    }

    /**
     * Increment shares count.
     */
    public void incrementShares() {
        this.sharesCount++;
    }

    /**
     * Increment views count.
     */
    public void incrementViews() {
        this.viewsCount++;
    }

    /**
     * Get total engagement (likes + comments + shares).
     * 
     * @return Total engagement count
     */
    public int getTotalEngagement() {
        return likesCount + commentsCount + sharesCount;
    }

    /**
     * Check if post has high engagement (for trending).
     * 
     * @param threshold Minimum engagement count
     * @return true if engagement >= threshold
     */
    public boolean hasHighEngagement(int threshold) {
        return getTotalEngagement() >= threshold;
    }

    /**
     * Get hashtags as a list.
     * 
     * @return List of hashtags without # symbol
     */
    public List<String> getHashtagList() {
        if (hashtags == null || hashtags.isEmpty()) {
            return new ArrayList<>();
        }
        return List.of(hashtags.split(","));
    }

    /**
     * Set hashtags from a list.
     * 
     * @param hashtagList List of hashtags
     */
    public void setHashtagList(List<String> hashtagList) {
        if (hashtagList == null || hashtagList.isEmpty()) {
            this.hashtags = null;
        } else {
            this.hashtags = String.join(",", hashtagList);
        }
    }

    /**
     * Get mentioned user IDs as a list.
     * 
     * @return List of user IDs mentioned
     */
    public List<Long> getMentionList() {
        if (mentions == null || mentions.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> mentionList = new ArrayList<>();
        for (String id : mentions.split(",")) {
            try {
                mentionList.add(Long.parseLong(id.trim()));
            } catch (NumberFormatException e) {
                // Skip invalid IDs
            }
        }
        return mentionList;
    }

    /**
     * Set mentions from a list of user IDs.
     * 
     * @param mentionList List of user IDs
     */
    public void setMentionList(List<Long> mentionList) {
        if (mentionList == null || mentionList.isEmpty()) {
            this.mentions = null;
        } else {
            this.mentions = mentionList.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
        }
    }

    /**
     * Get image URLs as a list.
     * 
     * @return List of image URLs
     */
    public List<String> getImageUrlList() {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return new ArrayList<>();
        }
        return List.of(imageUrls.split(","));
    }

    /**
     * Set image URLs from a list.
     * 
     * @param imageUrlList List of image URLs
     */
    public void setImageUrlList(List<String> imageUrlList) {
        if (imageUrlList == null || imageUrlList.isEmpty()) {
            this.imageUrls = null;
        } else {
            this.imageUrls = String.join(",", imageUrlList);
        }
    }
}

