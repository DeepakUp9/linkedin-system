package com.linkedin.notification.service;

import com.linkedin.common.exceptions.ResourceNotFoundException;
import com.linkedin.notification.dto.NotificationPreferenceDto;
import com.linkedin.notification.mapper.NotificationMapper;
import com.linkedin.notification.model.NotificationChannel;
import com.linkedin.notification.model.NotificationPreference;
import com.linkedin.notification.model.NotificationType;
import com.linkedin.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Service for managing user notification preferences.
 * 
 * Purpose:
 * - Manages which notification channels users want (email, in-app, push)
 * - Provides default preferences for new users
 * - Updates user preferences
 * 
 * Business Rules:
 * - At least one channel must be enabled per notification type
 * - Default preferences created for new users
 * - Preferences cached in Redis for performance
 * 
 * Example Flow:
 * <pre>
 * User Registration:
 *   1. User creates account
 *   2. User Service notifies Notification Service (Kafka)
 *   3. createDefaultPreferences(userId) called
 *   4. Default preferences saved for all notification types
 * 
 * User Updates Preference:
 *   1. User: "I don't want email for post likes"
 *   2. updatePreference(userId, POST_LIKED, {email=false, inApp=true})
 *   3. Validation: At least one channel enabled? ✅
 *   4. Save to database
 *   5. Invalidate cache
 * 
 * Notification Delivery:
 *   1. Event received: "User A liked User B's post"
 *   2. getPreference(userId=B, type=POST_LIKED)
 *   3. Preference says: email=false, inApp=true
 *   4. Send only in-app notification (skip email)
 * </pre>
 * 
 * @see NotificationPreference
 * @see NotificationPreferenceDto
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true) // Default to read-only
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationMapper mapper;

    // =========================================================================
    // Read Operations (Queries)
    // =========================================================================

    /**
     * Get all notification preferences for a user.
     * 
     * Use Case:
     * User opens notification settings page
     * 
     * Performance:
     * - Cached in Redis (key: "userPreferences:123")
     * - Cache TTL: 1 hour (configured in application.yml)
     * - Cache invalidated on update
     * 
     * If No Preferences Exist:
     * - Returns default preferences
     * - Doesn't save to database until user makes changes
     * 
     * @param userId The user ID
     * @return List of preferences for all notification types
     */
    @Cacheable(value = "userPreferences", key = "#userId")
    public List<NotificationPreferenceDto> getUserPreferences(Long userId) {
        log.debug("Fetching notification preferences for user {}", userId);
        
        List<NotificationPreference> preferences = preferenceRepository.findByUserId(userId);
        
        // If user has no preferences yet, return defaults
        if (preferences.isEmpty()) {
            log.debug("No preferences found for user {}. Returning defaults.", userId);
            return getDefaultPreferences();
        }
        
        // Check if user has preferences for all types (might be missing new types)
        if (preferences.size() < NotificationType.values().length) {
            log.debug("User {} has incomplete preferences. Filling missing types with defaults.", userId);
            preferences = ensureAllTypesHavePreferences(userId, preferences);
        }
        
        List<NotificationPreferenceDto> dtos = mapper.toPreferenceDtoList(preferences);
        log.info("Retrieved {} notification preferences for user {}", dtos.size(), userId);
        return dtos;
    }

    /**
     * Get preference for a specific notification type.
     * 
     * Use Case:
     * When delivering notification, check if user wants it via email/in-app
     * 
     * Performance:
     * - Cached in Redis (key: "userPreference:123:CONNECTION_ACCEPTED")
     * - Very fast lookup (no DB call if cached)
     * 
     * @param userId The user ID
     * @param type The notification type
     * @return Preference DTO
     */
    @Cacheable(value = "userPreference", key = "#userId + ':' + #type")
    public NotificationPreferenceDto getPreferenceForType(Long userId, NotificationType type) {
        log.debug("Fetching preference for user {} and type {}", userId, type);
        
        return preferenceRepository.findByUserIdAndNotificationType(userId, type)
            .map(mapper::toPreferenceDto)
            .orElseGet(() -> {
                log.debug("No preference found for user {} and type {}. Using default.", userId, type);
                return getDefaultPreferenceForType(type);
            });
    }

    /**
     * Check if user wants notification via specific channel.
     * 
     * Use Case:
     * Before sending email: "Does user want email for connection requests?"
     * 
     * Example:
     * <pre>
     * {@code
     * if (preferenceService.isChannelEnabled(userId, CONNECTION_REQUESTED, EMAIL)) {
     *     emailService.sendConnectionRequestEmail(...);
     * }
     * }
     * </pre>
     * 
     * @param userId The user ID
     * @param type Notification type
     * @param channel Delivery channel (EMAIL, IN_APP, PUSH)
     * @return true if channel is enabled
     */
    public boolean isChannelEnabled(Long userId, NotificationType type, NotificationChannel channel) {
        NotificationPreferenceDto pref = getPreferenceForType(userId, type);
        
        return switch (channel) {
            case EMAIL -> pref.isEmailEnabled();
            case IN_APP -> pref.isInAppEnabled();
            case PUSH -> pref.isPushEnabled();
            case SMS -> pref.isSmsEnabled();
        };
    }

    // =========================================================================
    // Write Operations (Commands)
    // =========================================================================

    /**
     * Update notification preference for a user.
     * 
     * Business Rules:
     * 1. At least one channel must be enabled
     * 2. Can't change notification type (must match existing)
     * 3. Creates new preference if doesn't exist
     * 
     * Cache Invalidation:
     * - Evicts user's full preference list
     * - Evicts specific type preference
     * 
     * @param userId The user ID
     * @param preferenceDto The updated preference
     * @return Updated preference
     * @throws com.linkedin.common.exceptions.ValidationException if no channels enabled
     */
    @Transactional
    @CacheEvict(value = {"userPreferences", "userPreference"}, key = "#userId", allEntries = true)
    public NotificationPreferenceDto updatePreference(Long userId, NotificationPreferenceDto preferenceDto) {
        log.info("Updating notification preference for user {} and type {}", 
            userId, preferenceDto.getNotificationType());
        
        // Validate: At least one channel must be enabled
        if (!preferenceDto.hasAnyChannelEnabled()) {
            log.warn("User {} attempted to disable all channels for type {}", 
                userId, preferenceDto.getNotificationType());
            throw new com.linkedin.common.exceptions.ValidationException(
                "At least one notification channel must be enabled", 
                "ALL_CHANNELS_DISABLED"
            );
        }
        
        // Find existing preference or create new one
        NotificationPreference preference = preferenceRepository
            .findByUserIdAndNotificationType(userId, preferenceDto.getNotificationType())
            .orElseGet(() -> {
                log.debug("Creating new preference for user {} and type {}", 
                    userId, preferenceDto.getNotificationType());
                NotificationPreference newPref = mapper.toPreferenceEntity(preferenceDto);
                newPref.setUserId(userId);
                return newPref;
            });
        
        // Update fields
        mapper.updatePreferenceFromDto(preferenceDto, preference);
        
        // Save
        NotificationPreference saved = preferenceRepository.save(preference);
        log.info("Successfully updated preference for user {} and type {}", 
            userId, preferenceDto.getNotificationType());
        
        return mapper.toPreferenceDto(saved);
    }

    /**
     * Batch update all preferences for a user.
     * 
     * Use Case:
     * User updates multiple preferences at once on settings page
     * 
     * Performance:
     * - Single transaction
     * - Single cache invalidation
     * 
     * @param userId The user ID
     * @param preferenceDtos List of preferences to update
     * @return Updated preferences
     */
    @Transactional
    @CacheEvict(value = {"userPreferences", "userPreference"}, key = "#userId", allEntries = true)
    public List<NotificationPreferenceDto> updatePreferences(
        Long userId, 
        List<NotificationPreferenceDto> preferenceDtos
    ) {
        log.info("Batch updating {} preferences for user {}", preferenceDtos.size(), userId);
        
        List<NotificationPreferenceDto> updated = preferenceDtos.stream()
            .map(dto -> updatePreferenceSilently(userId, dto)) // No cache eviction per item
            .toList();
        
        log.info("Successfully batch updated {} preferences for user {}", updated.size(), userId);
        return updated;
    }

    /**
     * Create default preferences for a new user.
     * 
     * Called When:
     * - New user registers
     * - User receives first notification (lazy initialization)
     * 
     * Default Settings:
     * - EMAIL: enabled for important types (connections)
     * - IN_APP: enabled for all types
     * - PUSH: disabled (user must opt-in)
     * - SMS: disabled (not implemented yet)
     * 
     * @param userId The new user's ID
     */
    @Transactional
    public void createDefaultPreferences(Long userId) {
        log.info("Creating default notification preferences for user {}", userId);
        
        // Check if preferences already exist
        long existingCount = preferenceRepository.findByUserId(userId).size();
        if (existingCount > 0) {
            log.debug("User {} already has {} preferences. Skipping default creation.", 
                userId, existingCount);
            return;
        }
        
        // Create preference for each notification type
        for (NotificationType type : NotificationType.values()) {
            NotificationPreference pref = NotificationPreference.builder()
                .userId(userId)
                .notificationType(type)
                .emailEnabled(isEmailEnabledByDefault(type))
                .inAppEnabled(true) // Always enabled
                .pushEnabled(false) // Opt-in only
                .smsEnabled(false)  // Not implemented
                .build();
            
            preferenceRepository.save(pref);
        }
        
        log.info("Created default preferences for {} notification types for user {}", 
            NotificationType.values().length, userId);
    }

    /**
     * Reset user's preferences to defaults.
     * 
     * Use Case:
     * User clicks "Reset to defaults" on settings page
     * 
     * @param userId The user ID
     */
    @Transactional
    @CacheEvict(value = {"userPreferences", "userPreference"}, key = "#userId", allEntries = true)
    public void resetToDefaults(Long userId) {
        log.info("Resetting preferences to defaults for user {}", userId);
        
        // Delete existing preferences
        int deleted = preferenceRepository.deleteByUserId(userId);
        log.debug("Deleted {} existing preferences for user {}", deleted, userId);
        
        // Create new defaults
        createDefaultPreferences(userId);
    }

    // =========================================================================
    // Helper Methods (Private)
    // =========================================================================

    /**
     * Get default preferences without saving to database.
     * 
     * @return List of default preferences
     */
    private List<NotificationPreferenceDto> getDefaultPreferences() {
        return Arrays.stream(NotificationType.values())
            .map(this::getDefaultPreferenceForType)
            .toList();
    }

    /**
     * Get default preference for a specific type.
     * 
     * @param type Notification type
     * @return Default preference
     */
    private NotificationPreferenceDto getDefaultPreferenceForType(NotificationType type) {
        return NotificationPreferenceDto.builder()
            .notificationType(type)
            .emailEnabled(isEmailEnabledByDefault(type))
            .inAppEnabled(true)
            .pushEnabled(false)
            .smsEnabled(false)
            .build();
    }

    /**
     * Determine if email should be enabled by default for a notification type.
     * 
     * Logic:
     * - Connection events: Email enabled (important)
     * - Post likes/comments: Email disabled (too noisy)
     * - System announcements: Email enabled (important)
     * 
     * @param type Notification type
     * @return true if email should be enabled by default
     */
    private boolean isEmailEnabledByDefault(NotificationType type) {
        return switch (type) {
            // Important: Enable email by default
            case CONNECTION_REQUESTED, CONNECTION_ACCEPTED, 
                 ACCOUNT_SECURITY, SYSTEM_ANNOUNCEMENT,
                 JOB_APPLICATION_STATUS -> true;
            
            // Less important: In-app only by default
            case POST_LIKED, POST_COMMENTED, POST_SHARED,
                 PROFILE_VIEWED, JOB_RECOMMENDATION,
                 NEW_MESSAGE -> false;
            
            // Negative events: No email
            case CONNECTION_REJECTED -> false;
        };
    }

    /**
     * Update preference without cache eviction (for batch operations).
     * 
     * @param userId The user ID
     * @param preferenceDto The preference to update
     * @return Updated preference
     */
    private NotificationPreferenceDto updatePreferenceSilently(
        Long userId, 
        NotificationPreferenceDto preferenceDto
    ) {
        NotificationPreference preference = preferenceRepository
            .findByUserIdAndNotificationType(userId, preferenceDto.getNotificationType())
            .orElseGet(() -> {
                NotificationPreference newPref = mapper.toPreferenceEntity(preferenceDto);
                newPref.setUserId(userId);
                return newPref;
            });
        
        mapper.updatePreferenceFromDto(preferenceDto, preference);
        NotificationPreference saved = preferenceRepository.save(preference);
        return mapper.toPreferenceDto(saved);
    }

    /**
     * Ensure user has preferences for all notification types.
     * Creates missing ones with defaults.
     * 
     * Use Case:
     * New notification types added to system, existing users don't have preferences yet
     * 
     * @param userId The user ID
     * @param existingPreferences Current preferences
     * @return Complete list of preferences
     */
    @Transactional
    private List<NotificationPreference> ensureAllTypesHavePreferences(
        Long userId, 
        List<NotificationPreference> existingPreferences
    ) {
        List<NotificationType> existingTypes = existingPreferences.stream()
            .map(NotificationPreference::getNotificationType)
            .toList();
        
        for (NotificationType type : NotificationType.values()) {
            if (!existingTypes.contains(type)) {
                log.debug("Creating missing preference for user {} and type {}", userId, type);
                NotificationPreference newPref = NotificationPreference.builder()
                    .userId(userId)
                    .notificationType(type)
                    .emailEnabled(isEmailEnabledByDefault(type))
                    .inAppEnabled(true)
                    .pushEnabled(false)
                    .smsEnabled(false)
                    .build();
                existingPreferences.add(preferenceRepository.save(newPref));
            }
        }
        
        return existingPreferences;
    }
}

