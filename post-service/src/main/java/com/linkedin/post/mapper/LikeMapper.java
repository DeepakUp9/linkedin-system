package com.linkedin.post.mapper;

import com.linkedin.post.dto.LikeResponseDto;
import com.linkedin.post.model.Like;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct Mapper for Like entity ↔ DTO conversion.
 * 
 * Purpose:
 * Automatically generates code for converting between:
 * - Like entity (database) ↔ LikeResponseDto (API response)
 * 
 * Note: Likes don't have request DTOs because:
 * - Liking is a simple POST with no body
 * - Unlike is a simple DELETE
 * - All data comes from JWT token (userId) and URL (postId/commentId)
 * 
 * Usage Example:
 * <pre>
 * {@code
 * @Service
 * public class LikeService {
 *     @Autowired
 *     private LikeMapper likeMapper;
 *     
 *     public Page<LikeResponseDto> getPostLikes(Long postId, Pageable pageable) {
 *         Page<Like> likes = likeRepository.findByPostIdAndCommentIdIsNull(postId, pageable);
 *         return likes.map(likeMapper::toDto);
 *     }
 * }
 * }
 * </pre>
 * 
 * Enrichment Flow:
 * <pre>
 * Like entity (userId, postId, commentId)
 *   ↓
 * LikeMapper.toDto()
 *   ↓
 * LikeResponseDto (basic fields)
 *   ↓
 * Enrich with User data (Feign → User Service)
 *   ↓
 * LikeResponseDto (with user.displayName, user.profilePictureUrl, etc.)
 * </pre>
 * 
 * @see Like
 * @see LikeResponseDto
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface LikeMapper {

    // =========================================================================
    // Entity → Response DTO (Database → API Response)
    // =========================================================================
    
    /**
     * Convert Like entity to LikeResponseDto.
     * 
     * Automatic Mappings:
     * - id → id
     * - postId → postId
     * - commentId → commentId
     * - createdAt → createdAt
     * 
     * Custom Mappings:
     * - isPostLike (computed from commentId)
     * - isCommentLike (computed from commentId)
     * 
     * Manual Enrichment (in service layer):
     * - user (from User Service via Feign)
     *   - user.id (from like.userId)
     *   - user.displayName
     *   - user.headline
     *   - user.profilePictureUrl
     *   - user.isConnection (from Connection Service)
     * 
     * @param like Like entity
     * @return LikeResponseDto
     */
    @Mapping(target = "isPostLike", expression = "java(like.isPostLike())")
    @Mapping(target = "isCommentLike", expression = "java(like.isCommentLike())")
    @Mapping(target = "user", ignore = true) // Enriched in service
    LikeResponseDto toDto(Like like);
    
    /**
     * Convert list of Like entities to list of LikeResponseDtos.
     * 
     * Batch mapping for collections.
     * Useful for "Who liked this post" pages.
     * 
     * @param likes List of Like entities
     * @return List of LikeResponseDtos
     */
    List<LikeResponseDto> toDtoList(List<Like> likes);
}

