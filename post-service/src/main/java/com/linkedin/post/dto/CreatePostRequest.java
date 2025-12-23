package com.linkedin.post.dto;

import com.linkedin.post.model.PostType;
import com.linkedin.post.model.PostVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for creating a new post.
 * 
 * Purpose:
 * Request payload when user creates a post via REST API.
 * Validates input before processing.
 * 
 * Design Pattern: DTO Pattern
 * - Separates API contract from internal entity structure
 * - Enables input validation via Bean Validation annotations
 * - Prevents exposing sensitive entity fields
 * 
 * API Request Example:
 * <pre>
 * POST /api/posts
 * Content-Type: application/json
 * Authorization: Bearer {JWT_TOKEN}
 * 
 * {
 *   "content": "Excited to announce my new role at Google! #NewJob #Excited",
 *   "type": "TEXT",
 *   "visibility": "PUBLIC",
 *   "imageUrls": [],
 *   "sharedPostId": null
 * }
 * </pre>
 * 
 * Validation Rules:
 * - content: Required, 1-3000 characters
 * - type: Required, valid PostType enum
 * - visibility: Required, valid PostVisibility enum
 * - imageUrls: Optional, max 10 images
 * - sharedPostId: Optional, for reposts
 * 
 * Flow:
 * <pre>
 * Frontend sends JSON
 *   ↓
 * Spring deserializes to CreatePostRequest
 *   ↓
 * @Valid annotation triggers validation
 *   ↓
 * If valid: Pass to Service layer
 * If invalid: Return 400 Bad Request with error details
 * </pre>
 * 
 * @see com.linkedin.post.controller.PostController#createPost
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePostRequest {

    /**
     * Main text content of the post.
     * 
     * Constraints:
     * - Cannot be blank
     * - Must be 1-3000 characters
     * 
     * Examples:
     * - "Just finished an amazing course on Spring Boot!"
     * - "Check out my latest article: [link]"
     * - "Looking for Java developers in San Francisco. DM me!"
     */
    @NotBlank(message = "Content cannot be empty")
    @Size(min = 1, max = 3000, message = "Content must be between 1 and 3000 characters")
    private String content;

    /**
     * Type of post being created.
     * 
     * Valid values:
     * - TEXT: Pure text post
     * - IMAGE: Post with images
     * - ARTICLE: Long-form article
     * - VIDEO: Post with video (future)
     * - POLL: Poll/survey (future)
     * 
     * Default: TEXT (if not specified)
     */
    @NotNull(message = "Post type is required")
    private PostType type;

    /**
     * Who can see this post.
     * 
     * Valid values:
     * - PUBLIC: Anyone can see
     * - CONNECTIONS_ONLY: Only connections
     * - PRIVATE: Only me
     * 
     * Default: CONNECTIONS_ONLY (if not specified)
     */
    @NotNull(message = "Visibility is required")
    private PostVisibility visibility;

    /**
     * URLs of uploaded images (for IMAGE type posts).
     * 
     * Constraints:
     * - Optional (can be null or empty)
     * - Max 10 images
     * 
     * Note: Images should be uploaded to storage (S3) first,
     * then URLs passed here.
     * 
     * Example:
     * [
     *   "https://s3.amazonaws.com/linkedin-posts/img1.jpg",
     *   "https://s3.amazonaws.com/linkedin-posts/img2.jpg"
     * ]
     */
    @Size(max = 10, message = "Maximum 10 images allowed")
    private List<String> imageUrls;

    /**
     * ID of original post if this is a share/repost.
     * 
     * Use Case:
     * - User shares someone else's post with their own comment
     * - This post becomes a "repost" with reference to original
     * 
     * Null for original posts.
     * Set to original post ID for shares.
     * 
     * Example:
     * {
     *   "content": "Great insights! Everyone should read this.",
     *   "type": "TEXT",
     *   "visibility": "PUBLIC",
     *   "sharedPostId": 123,  ← Sharing post ID 123
     *   "imageUrls": null
     * }
     */
    private Long sharedPostId;

    /**
     * Additional comment when sharing a post.
     * 
     * Only applicable if sharedPostId is set.
     * 
     * This is the user's personal comment about why they're sharing.
     * 
     * Example:
     * "This article changed my perspective on microservices!"
     */
    @Size(max = 500, message = "Share comment must be less than 500 characters")
    private String shareComment;

    /**
     * Validate that IMAGE type posts have at least one image.
     * 
     * Custom validation logic.
     * 
     * @return true if valid
     */
    public boolean isValid() {
        // If type is IMAGE, must have at least one image URL
        if (type == PostType.IMAGE) {
            return imageUrls != null && !imageUrls.isEmpty();
        }
        return true;
    }
}

