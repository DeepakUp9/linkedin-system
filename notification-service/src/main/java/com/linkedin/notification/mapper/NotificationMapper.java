package com.linkedin.notification.mapper;

import com.linkedin.notification.dto.NotificationPreferenceDto;
import com.linkedin.notification.dto.NotificationResponseDto;
import com.linkedin.notification.model.Notification;
import com.linkedin.notification.model.NotificationPreference;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for converting between entities and DTOs.
 * 
 * Purpose:
 * - Automatically converts Notification entity ↔ NotificationResponseDto
 * - Automatically converts NotificationPreference entity ↔ NotificationPreferenceDto
 * - Handles nested objects and lists
 * - Type-safe at compile time
 * 
 * How MapStruct Works:
 * <pre>
 * 1. You define mapping methods (interfaces, no implementation!)
 * 2. MapStruct generates implementation at compile time
 * 3. Spring registers implementation as a bean
 * 4. You inject and use it
 * </pre>
 * 
 * Example Usage:
 * <pre>
 * {@code
 * @Service
 * public class NotificationService {
 *     @Autowired
 *     private NotificationMapper mapper;
 *     
 *     public NotificationResponseDto getNotification(Long id) {
 *         Notification entity = repository.findById(id).orElseThrow();
 *         return mapper.toDto(entity); // Magic! ✨
 *     }
 * }
 * }
 * </pre>
 * 
 * Configuration:
 * - componentModel = "spring": Generates Spring bean
 * - unmappedTargetPolicy = WARN: Warns about unmapped fields
 * - injectionStrategy = CONSTRUCTOR: Uses constructor injection
 * 
 * @see Notification
 * @see NotificationResponseDto
 * @see NotificationPreference
 * @see NotificationPreferenceDto
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.WARN,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface NotificationMapper {

    // =========================================================================
    // Notification Entity ↔ DTO Mappings
    // =========================================================================

    /**
     * Convert Notification entity to NotificationResponseDto.
     * 
     * Automatic Mappings (same field names):
     * - id → id
     * - type → type
     * - channel → channel
     * - title → title
     * - message → message
     * - actionLink → actionLink
     * - iconUrl → iconUrl
     * - createdAt → createdAt
     * - readAt → readAt
     * 
     * Custom Mappings:
     * - isRead (entity) → isRead (dto): Handled by @Mapping
     * - relatedEntityType + relatedEntityId → relatedEntity (nested DTO)
     * 
     * MapStruct Generation:
     * <pre>
     * public class NotificationMapperImpl implements NotificationMapper {
     *     public NotificationResponseDto toDto(Notification entity) {
     *         if (entity == null) return null;
     *         
     *         NotificationResponseDto dto = new NotificationResponseDto();
     *         dto.setId(entity.getId());
     *         dto.setType(entity.getType());
     *         // ... all other fields
     *         dto.setRead(entity.isRead()); // boolean getter
     *         dto.setRelatedEntity(mapRelatedEntity(entity));
     *         
     *         return dto;
     *     }
     * }
     * </pre>
     * 
     * @param entity The Notification entity
     * @return NotificationResponseDto
     */
    @Mapping(source = "read", target = "isRead")
    @Mapping(target = "relatedEntity", expression = "java(mapRelatedEntity(entity))")
    NotificationResponseDto toDto(Notification entity);

    /**
     * Convert list of Notification entities to list of DTOs.
     * 
     * Why We Need This:
     * - Common use case: Get all notifications for a user
     * - MapStruct generates efficient list conversion
     * 
     * Generated Code:
     * <pre>
     * public List<NotificationResponseDto> toDtoList(List<Notification> entities) {
     *     if (entities == null) return null;
     *     List<NotificationResponseDto> list = new ArrayList<>(entities.size());
     *     for (Notification entity : entities) {
     *         list.add(toDto(entity)); // Reuses single mapping method
     *     }
     *     return list;
     * }
     * </pre>
     * 
     * @param entities List of Notification entities
     * @return List of NotificationResponseDto
     */
    List<NotificationResponseDto> toDtoList(List<Notification> entities);

    // =========================================================================
    // NotificationPreference Entity ↔ DTO Mappings
    // =========================================================================

    /**
     * Convert NotificationPreference entity to NotificationPreferenceDto.
     * 
     * Automatic Mappings:
     * - notificationType → notificationType
     * - emailEnabled → emailEnabled
     * - inAppEnabled → inAppEnabled
     * - pushEnabled → pushEnabled
     * - smsEnabled → smsEnabled
     * 
     * Note: userId is NOT mapped (internal field, not exposed in DTO)
     * 
     * @param entity The NotificationPreference entity
     * @return NotificationPreferenceDto
     */
    @Mapping(target = "notificationType", source = "notificationType")
    NotificationPreferenceDto toPreferenceDto(NotificationPreference entity);

    /**
     * Convert NotificationPreferenceDto to NotificationPreference entity.
     * 
     * Use Case:
     * When user updates preferences via API:
     * PUT /api/notifications/preferences
     * {
     *   "notificationType": "CONNECTION_ACCEPTED",
     *   "emailEnabled": false,
     *   "inAppEnabled": true
     * }
     * 
     * We convert DTO → Entity to save in database.
     * 
     * Note: userId must be set separately (comes from authentication context)
     * 
     * @param dto The NotificationPreferenceDto
     * @return NotificationPreference entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true) // Set separately in service
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    NotificationPreference toPreferenceEntity(NotificationPreferenceDto dto);

    /**
     * Convert list of NotificationPreference entities to DTOs.
     * 
     * Use Case:
     * GET /api/notifications/preferences
     * Returns all preferences for authenticated user
     * 
     * @param entities List of NotificationPreference entities
     * @return List of NotificationPreferenceDto
     */
    List<NotificationPreferenceDto> toPreferenceDtoList(List<NotificationPreference> entities);

    /**
     * Update existing NotificationPreference entity from DTO.
     * 
     * Use Case:
     * User updates single preference without changing others
     * 
     * How It Works:
     * - Loads existing entity from database
     * - Updates only non-null fields from DTO
     * - Saves back to database
     * 
     * Configuration:
     * - @BeanMapping: Ignore null values (for partial updates)
     * - @MappingTarget: Modifies existing entity in-place
     * 
     * Example:
     * <pre>
     * {@code
     * // User wants to disable email for CONNECTION_ACCEPTED
     * NotificationPreference existing = repository.findByUserIdAndType(...);
     * // existing: {emailEnabled=true, inAppEnabled=true, pushEnabled=false}
     * 
     * NotificationPreferenceDto dto = new NotificationPreferenceDto();
     * dto.setEmailEnabled(false); // Only set what changed
     * 
     * mapper.updatePreferenceFromDto(dto, existing);
     * // existing now: {emailEnabled=false, inAppEnabled=true, pushEnabled=false}
     * 
     * repository.save(existing);
     * }
     * </pre>
     * 
     * @param dto The source DTO with updated values
     * @param entity The target entity to update
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "notificationType", ignore = true) // Can't change type
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updatePreferenceFromDto(NotificationPreferenceDto dto, @MappingTarget NotificationPreference entity);

    // =========================================================================
    // Helper Methods (Custom Logic)
    // =========================================================================

    /**
     * Map related entity fields to nested DTO.
     * 
     * Why Custom Method?
     * - Entity has 2 separate fields: relatedEntityType, relatedEntityId
     * - DTO has 1 nested object: relatedEntity {type, id}
     * - MapStruct can't automatically map this, so we write custom logic
     * 
     * Called By:
     * MapStruct-generated toDto() method
     * 
     * Logic:
     * <pre>
     * Entity: relatedEntityType="CONNECTION", relatedEntityId="123"
     * ↓
     * DTO: relatedEntity={entityType="CONNECTION", entityId="123"}
     * </pre>
     * 
     * @param entity The Notification entity
     * @return RelatedEntityDto or null
     */
    default NotificationResponseDto.RelatedEntityDto mapRelatedEntity(Notification entity) {
        if (entity == null || entity.getRelatedEntityType() == null) {
            return null;
        }
        
        return NotificationResponseDto.RelatedEntityDto.builder()
            .entityType(entity.getRelatedEntityType())
            .entityId(entity.getRelatedEntityId())
            .build();
    }

    /**
     * Map nested DTO back to entity fields (reverse of above).
     * 
     * Use Case:
     * If we ever need to create Notification from DTO
     * (Currently not used, but good for completeness)
     * 
     * @param relatedEntity The nested DTO
     * @param entity The target entity to populate
     */
    default void mapRelatedEntityToEntity(
        NotificationResponseDto.RelatedEntityDto relatedEntity, 
        @MappingTarget Notification entity
    ) {
        if (relatedEntity == null) {
            entity.setRelatedEntityType(null);
            entity.setRelatedEntityId(null);
        } else {
            entity.setRelatedEntityType(relatedEntity.getEntityType());
            entity.setRelatedEntityId(relatedEntity.getEntityId());
        }
    }
}

