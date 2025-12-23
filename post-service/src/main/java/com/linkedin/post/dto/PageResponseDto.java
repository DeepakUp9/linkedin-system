package com.linkedin.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic DTO for paginated responses.
 * 
 * Purpose:
 * Wrapper for paginated data with metadata.
 * Reusable across different entity types.
 * 
 * Design Pattern: Generic DTO
 * - Type parameter T for flexibility
 * - Contains data + pagination metadata
 * - Consistent pagination format across all endpoints
 * 
 * API Response Example:
 * <pre>
 * GET /api/posts?page=0&size=20
 * 
 * {
 *   "content": [
 *     {...post 1...},
 *     {...post 2...},
 *     ...
 *   ],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 156,
 *   "totalPages": 8,
 *   "hasNext": true,
 *   "hasPrevious": false,
 *   "isFirst": true,
 *   "isLast": false
 * }
 * </pre>
 * 
 * Usage Examples:
 * <pre>
 * {@code
 * // Posts
 * PageResponseDto<PostResponseDto> posts = ...
 * 
 * // Comments
 * PageResponseDto<CommentResponseDto> comments = ...
 * 
 * // Likes
 * PageResponseDto<LikeResponseDto> likes = ...
 * }
 * </pre>
 * 
 * Converting from Spring Data Page:
 * <pre>
 * {@code
 * Page<Post> page = postRepository.findAll(pageable);
 * PageResponseDto<PostResponseDto> response = PageResponseDto.from(
 *     page.map(postMapper::toDto)
 * );
 * }
 * </pre>
 * 
 * @param <T> The type of content (PostResponseDto, CommentResponseDto, etc.)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponseDto<T> {

    /**
     * List of items in current page.
     */
    private List<T> content;

    /**
     * Current page number (0-indexed).
     */
    private Integer page;

    /**
     * Number of items per page.
     */
    private Integer size;

    /**
     * Total number of items across all pages.
     */
    private Long totalElements;

    /**
     * Total number of pages.
     */
    private Integer totalPages;

    /**
     * Whether there's a next page.
     */
    private Boolean hasNext;

    /**
     * Whether there's a previous page.
     */
    private Boolean hasPrevious;

    /**
     * Whether this is the first page.
     */
    private Boolean isFirst;

    /**
     * Whether this is the last page.
     */
    private Boolean isLast;

    /**
     * Helper method to create PageResponseDto from Spring Data Page.
     * 
     * @param page Spring Data Page object
     * @param <T> Type of content
     * @return PageResponseDto
     */
    public static <T> PageResponseDto<T> from(org.springframework.data.domain.Page<T> page) {
        return PageResponseDto.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .build();
    }
}

