package com.linkedin.notification.model;

import lombok.Getter;

/**
 * Enum representing the type of notification.
 * 
 * Types correspond to different events in the system:
 * - Connection events (requested, accepted, rejected)
 * - Post events (liked, commented)
 * - Job events (applied, status changed)
 * - etc.
 */
@Getter
public enum NotificationType {
    // Connection-related notifications
    CONNECTION_REQUESTED("Connection Request", "Someone sent you a connection request"),
    CONNECTION_ACCEPTED("Connection Accepted", "Someone accepted your connection request"),
    CONNECTION_REJECTED("Connection Rejected", "Someone rejected your connection request"),
    
    // Post-related notifications (future)
    POST_LIKED("Post Liked", "Someone liked your post"),
    POST_COMMENTED("Post Comment", "Someone commented on your post"),
    POST_SHARED("Post Shared", "Someone shared your post"),
    
    // Profile-related notifications (future)
    PROFILE_VIEWED("Profile View", "Someone viewed your profile"),
    
    // Job-related notifications (future)
    JOB_APPLICATION_STATUS("Application Status", "Your job application status changed"),
    JOB_RECOMMENDATION("Job Recommendation", "New job recommendations for you"),
    
    // Message notifications (future)
    NEW_MESSAGE("New Message", "You have a new message"),
    
    // System notifications
    SYSTEM_ANNOUNCEMENT("System Announcement", "Important system notification"),
    ACCOUNT_SECURITY("Account Security", "Security alert for your account");

    private final String displayName;
    private final String description;

    NotificationType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}

