package com.linkedin.post.mapper;

import com.linkedin.post.dto.CreatePostRequest;
import com.linkedin.post.dto.PostResponseDto;
import com.linkedin.post.dto.UpdatePostRequest;
import com.linkedin.post.model.Post;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct Mapper for Post entity ↔ DTO conversion.
 * 
 * Purpose:
 * Automatically generates code for converting between:
 * - Post entity (database) ↔ PostResponseDto (API response)
 * - CreatePostRequest (API request) ↔ Post entity
 * 
 * Design Pattern: Mapper Pattern
 * - Separates mapping logic from business logic
 * - Compile-time code generation (no reflection!)
 * - Type-safe conversions
 * - Automatic null handling
 * 
 * How MapStruct Works:
 * 
 * 1. You write interface with method signatures:
 * <pre>
 * {@code
 * @Mapper
 * public interface PostMapper {
 *     PostResponseDto toDto(Post post);
 * }
 * }
 * </pre>
 * 
 * 2. MapStruct generates implementation at compile time:
 * <pre>
 * {@code
 * @Component
 * public class PostMapperImpl implements PostMapper {
 *     public PostResponseDto toDto(Post post) {
 *         if (post == null) return null;
 *         
 *         PostResponseDto dto = new PostResponseDto();
 *         dto.setId(post.getId());
 *         dto.setContent(post.getContent());
 *         dto.setType(post.getType());
 *         // ... 50+ more lines generated automatically!
 *         
 *         return dto;
 *     }
 * }
 * }
 * </pre>
 * 
 * 3. Use in service:
 * <pre>
 * {@code
 * @Service
 * public class PostService {
 *     @Autowired
 *     private PostMapper postMapper; // Inject generated implementation
 *     
 *     public PostResponseDto getPost(Long id) {
 *         Post post = postRepository.findById(id).orElseThrow();
 *         return postMapper.toDto(post); // Automatic mapping!
 *     }
 * }
 * }
 * </pre>
 * 
 * Custom Mappings:
 * 
 * Use @Mapping annotation for custom field mappings:
 * <pre>
 * {@code
 * @Mapping(source = "authorId", target = "author.id")
 * PostResponseDto toDto(Post post);
 * }
 * </pre>
 * 
 * Benefits:
 * ✅ No boilerplate code
 * ✅ Compile-time checking (catches errors early)
 * ✅ Fast (no reflection, direct method calls)
 * ✅ Easy to test (can mock interface)
 * ✅ Maintainable (centralized mapping logic)
 * 
 * @see Post
 * @see PostResponseDto
 * @see CreatePostRequest
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface PostMapper {

    // =========================================================================
    // Entity → Response DTO (Database → API Response)
    // =========================================================================
    
    /**
     * Convert Post entity to PostResponseDto.
     * 
     * Automatic Mappings (same field names):
     * - id → id
     * - type → type
     * - content → content
     * - visibility → visibility
     * - createdAt → createdAt
     * - updatedAt → updatedAt
     * 
     * Custom Mappings (defined below):
     * - imageUrls (String) → imageUrls (List<String>)
     * - hashtags (String) → hashtags (List<String>)
     * - mentions (String) → mentions (List<Long>)
     * - engagement fields → engagement (nested DTO)
     * 
     * Manual Enrichment (in service layer):
     * - author (from User Service)
     * - hasLiked (from Like repository)
     * - canEdit, canDelete (business logic)
     * - sharedPost (recursive, if applicable)
     * 
     * @param post Post entity
     * @return PostResponseDto
     */
    @Mapping(target = "imageUrls", expression = "java(post.getImageUrlList())")
    @Mapping(target = "hashtags", expression = "java(post.getHashtagList())")
    @Mapping(target = "mentions", expression = "java(post.getMentionList())")
    @Mapping(target = "engagement", expression = "java(toEngagementDto(post))")
    @Mapping(target = "author", ignore = true) // Enriched in service
    @Mapping(target = "hasLiked", ignore = true) // Computed in service
    @Mapping(target = "isAuthor", ignore = true) // Computed in service
    @Mapping(target = "canEdit", ignore = true) // Computed in service
    @Mapping(target = "canDelete", ignore = true) // Computed in service
    @Mapping(target = "sharedPost", ignore = true) // Handled separately in service
    PostResponseDto toDto(Post post);
    
    /**
     * Convert list of Post entities to list of PostResponseDtos.
     * 
     * Batch mapping for collections.
     * Uses toDto() method for each item.
     * 
     * @param posts List of Post entities
     * @return List of PostResponseDtos
     */
    List<PostResponseDto> toDtoList(List<Post> posts);

    // =========================================================================
    // Request DTO → Entity (API Request → Database)
    // =========================================================================
    
    /**
     * Convert CreatePostRequest to Post entity.
     * 
     * Automatic Mappings:
     * - content → content
     * - type → type
     * - visibility → visibility
     * - sharedPostId → sharedPostId
     * - shareComment → shareComment
     * 
     * Custom Mappings:
     * - imageUrls (List<String>) → imageUrls (String)
     * 
     * Fields set by service:
     * - id (auto-generated by database)
     * - authorId (from JWT token)
     * - hashtags (extracted from content)
     * - mentions (extracted from content)
     * - engagement counts (initialized to 0)
     * - audit fields (auto-populated by JPA)
     * 
     * @param request CreatePostRequest
     * @return Post entity (partially populated)
     */
    @Mapping(target = "id", ignore = true) // Auto-generated
    @Mapping(target = "authorId", ignore = true) // Set in service
    @Mapping(target = "imageUrls", expression = "java(joinList(request.getImageUrls()))")
    @Mapping(target = "hashtags", ignore = true) // Extracted in service
    @Mapping(target = "mentions", ignore = true) // Extracted in service
    @Mapping(target = "likesCount", constant = "0")
    @Mapping(target = "commentsCount", constant = "0")
    @Mapping(target = "sharesCount", constant = "0")
    @Mapping(target = "viewsCount", constant = "0")
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true) // Auto-populated by JPA
    @Mapping(target = "updatedAt", ignore = true) // Auto-populated by JPA
    Post toEntity(CreatePostRequest request);
    
    /**
     * Update existing Post entity from UpdatePostRequest.
     * 
     * Only updates fields present in UpdatePostRequest:
     * - content
     * - visibility
     * 
     * All other fields remain unchanged.
     * 
     * Strategy: IGNORE nulls and unmapped properties
     * 
     * @param request UpdatePostRequest
     * @param post Existing Post entity (will be modified)
     */
    @Mapping(target = "id", ignore = true) // Never change ID
    @Mapping(target = "authorId", ignore = true) // Never change author
    @Mapping(target = "type", ignore = true) // Type is immutable
    @Mapping(target = "imageUrls", ignore = true) // Images are immutable
    @Mapping(target = "sharedPostId", ignore = true) // Share is immutable
    @Mapping(target = "shareComment", ignore = true) // Share comment is immutable
    @Mapping(target = "hashtags", ignore = true) // Will be re-extracted in service
    @Mapping(target = "mentions", ignore = true) // Will be re-extracted in service
    @Mapping(target = "likesCount", ignore = true) // Don't reset counts
    @Mapping(target = "commentsCount", ignore = true)
    @Mapping(target = "sharesCount", ignore = true)
    @Mapping(target = "viewsCount", ignore = true)
    @Mapping(target = "deleted", ignore = true) // Don't change delete status
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true) // Don't change creation time
    @Mapping(target = "updatedAt", ignore = true) // Auto-updated by JPA
    void updateEntity(UpdatePostRequest request, @MappingTarget Post post);

    // =========================================================================
    // Helper Methods (Custom Mapping Logic)
    // =========================================================================
    
    /**
     * Create EngagementDto from Post entity.
     * 
     * Maps engagement fields to nested DTO.
     * 
     * @param post Post entity
     * @return EngagementDto
     */
    default PostResponseDto.EngagementDto toEngagementDto(Post post) {
        if (post == null) {
            return null;
        }
        
        return PostResponseDto.EngagementDto.builder()
                .likesCount(post.getLikesCount())
                .commentsCount(post.getCommentsCount())
                .sharesCount(post.getSharesCount())
                .viewsCount(post.getViewsCount())
                .totalEngagement(post.getTotalEngagement())
                .build();
    }
    
    /**
     * Join list of strings into comma-separated string.
     * 
     * Used for: imageUrls (List<String> → String)
     * 
     * Example:
     * ["url1", "url2", "url3"] → "url1,url2,url3"
     * 
     * @param list List of strings
     * @return Comma-separated string
     */
    default String joinList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return String.join(",", list);
    }
    
    /**
     * Split comma-separated string into list of strings.
     * 
     * Used for: imageUrls (String → List<String>)
     * 
     * Example:
     * "url1,url2,url3" → ["url1", "url2", "url3"]
     * 
     * @param str Comma-separated string
     * @return List of strings
     */
    default List<String> splitString(String str) {
        if (str == null || str.isEmpty()) {
            return List.of();
        }
        return List.of(str.split(","));
    }
}

