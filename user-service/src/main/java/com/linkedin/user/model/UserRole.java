package com.linkedin.user.model;

/**
 * Enum representing user roles in the system for role-based access control (RBAC).
 * 
 * Design Pattern: Rich Enum (provides metadata and behavior beyond simple constants)
 * 
 * Roles:
 * - USER: Regular user with standard permissions (view profile, edit own profile, etc.)
 * - ADMIN: Administrative user with elevated permissions (delete users, manage system, etc.)
 * 
 * Usage:
 * - Stored in User entity as a Set<UserRole>
 * - Used in @PreAuthorize annotations for endpoint protection
 * - Converted to Spring Security GrantedAuthority
 * 
 * Example:
 * @PreAuthorize("hasRole('ADMIN')")
 * public void deleteUser(Long id) { ... }
 * 
 * Future Extensions:
 * - MODERATOR: Can moderate content but not full admin
 * - PREMIUM_USER: Special permissions for premium users
 * - RECRUITER: Specific permissions for recruiters
 */
public enum UserRole {
    
    /**
     * Regular user role.
     * Permissions:
     * - View own profile
     * - Edit own profile
     * - Create posts
     * - Connect with others
     * - Search users
     */
    USER("User", "Regular user with standard permissions"),
    
    /**
     * Administrator role.
     * Permissions:
     * - All USER permissions
     * - Delete any user
     * - View all users
     * - Manage system settings
     * - Access admin dashboard
     */
    ADMIN("Administrator", "Administrative user with elevated permissions");
    
    // ==================== Fields ====================
    
    private final String displayName;
    private final String description;
    
    // ==================== Constructor ====================
    
    /**
     * Constructor for UserRole enum.
     * 
     * @param displayName Human-readable name for the role
     * @param description Detailed description of role permissions
     */
    UserRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    // ==================== Getters ====================
    
    /**
     * Get the human-readable display name.
     * 
     * @return Display name (e.g., "Administrator")
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get the role description.
     * 
     * @return Description of role permissions
     */
    public String getDescription() {
        return description;
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Get the Spring Security authority name.
     * Spring Security requires authorities to be prefixed with "ROLE_".
     * 
     * Example: UserRole.USER → "ROLE_USER"
     * 
     * @return Spring Security authority name
     */
    public String getAuthority() {
        return "ROLE_" + this.name();
    }
    
    /**
     * Check if this role has administrative privileges.
     * 
     * @return true if this is an ADMIN role
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }
    
    /**
     * Check if this role is a regular user.
     * 
     * @return true if this is a USER role
     */
    public boolean isUser() {
        return this == USER;
    }
    
    /**
     * Get all available roles as an array.
     * Useful for validation and display purposes.
     * 
     * @return Array of all UserRole values
     */
    public static UserRole[] getAllRoles() {
        return values();
    }
    
    /**
     * Check if a role name is valid.
     * 
     * @param roleName Role name to check
     * @return true if valid role name
     */
    public static boolean isValidRole(String roleName) {
        if (roleName == null) {
            return false;
        }
        try {
            valueOf(roleName.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * String representation of the role.
     * 
     * @return Display name of the role
     */
    @Override
    public String toString() {
        return displayName;
    }
}

