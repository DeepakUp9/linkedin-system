package com.linkedin.post.dto;

import com.linkedin.post.model.PostVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating an existing post.
 * 
 * Purpose:
 * Request payload when user edits their post.
 * Only allows updating content and visibility (not type or images).
 * 
 * Business Rules:
 * - Only author can edit their own post
 * - Cannot edit deleted posts
 * - Cannot change post type after creation
 * - Cannot change images after creation (for simplicity)
 * - Can change content and visibility
 * 
 * API Request Example:
 * <pre>
 * PUT /api/posts/{postId}
 * Content-Type: application/json
 * Authorization: Bearer {JWT_TOKEN}
 * 
 * {
 *   "content": "Updated content with corrections!",
 *   "visibility": "CONNECTIONS_ONLY"
 * }
 * </pre>
 * 
 * Why Limited Fields?
 * - Prevents accidental data corruption
 * - Type change could break UI expectations
 * - Image changes require re-upload (separate endpoint)
 * - Simpler validation and business logic
 * 
 * @see com.linkedin.post.controller.PostController#updatePost
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePostRequest {

    /**
     * Updated text content.
     * 
     * Constraints:
     * - Cannot be blank
     * - Must be 1-3000 characters
     * 
     * Use Case:
     * - Fix typos
     * - Add more information
     * - Correct mistakes
     */
    @NotBlank(message = "Content cannot be empty")
    @Size(min = 1, max = 3000, message = "Content must be between 1 and 3000 characters")
    private String content;

    /**
     * Updated visibility setting.
     * 
     * Use Cases:
     * - Change public post to connections only
     * - Make private draft public
     * - Restrict audience after posting
     * 
     * Note: Cannot change visibility if post has been shared
     * (enforced in service layer)
     */
    private PostVisibility visibility;
}

