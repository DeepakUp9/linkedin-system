package com.linkedin.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating an existing comment.
 * 
 * Purpose:
 * Request payload when user edits their comment.
 * Only allows updating content (not parent relationship).
 * 
 * Business Rules:
 * - Only author can edit their own comment
 * - Cannot edit deleted comments
 * - Cannot change parent comment (structure is immutable)
 * - Sets is_edited flag and edited_at timestamp
 * 
 * API Request Example:
 * <pre>
 * PUT /api/comments/{commentId}
 * Content-Type: application/json
 * Authorization: Bearer {JWT_TOKEN}
 * 
 * {
 *   "content": "Updated comment with corrected typo!"
 * }
 * </pre>
 * 
 * Why Only Content?
 * - Changing parent would break comment thread structure
 * - Moving comments between posts would confuse users
 * - Simple validation and business logic
 * 
 * @see com.linkedin.post.controller.CommentController#updateComment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCommentRequest {

    /**
     * Updated text content.
     * 
     * Constraints:
     * - Cannot be blank
     * - Must be 1-1000 characters
     * 
     * Use Cases:
     * - Fix typos
     * - Clarify meaning
     * - Add more context
     * 
     * Note: edited flag will be set to true,
     * and edited_at timestamp will be updated.
     */
    @NotBlank(message = "Comment content cannot be empty")
    @Size(min = 1, max = 1000, message = "Comment must be between 1 and 1000 characters")
    private String content;
}

