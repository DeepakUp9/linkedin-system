package com.linkedin.user.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * User entity representing a user account in the LinkedIn-like system.
 * 
 * This entity maps to the 'users' table in PostgreSQL and represents
 * the core user domain model. It extends BaseAuditEntity to inherit
 * audit fields (createdAt, updatedAt, createdBy, updatedBy).
 * 
 * Design Pattern Connections:
 * 1. Factory Pattern: Created by UserFactory
 * 2. Strategy Pattern: accountType determines validation strategy
 * 3. Repository Pattern: Persisted via UserRepository
 * 
 * Database Design:
 * - Primary Key: Auto-generated Long ID
 * - Unique Constraint: Email must be unique
 * - Indexes: email (for fast lookups), accountType (for filtering)
 * - Audit Fields: Inherited from BaseAuditEntity
 * 
 * Account Types:
 * - BASIC: Free tier with limited features
 * - PREMIUM: Paid tier with advanced features
 * 
 * @see BaseAuditEntity
 * @see AccountType
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_account_type", columnList = "account_type")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"password"})  // Never log passwords
public class User extends BaseAuditEntity {

    /**
     * Primary key - Auto-generated unique identifier
     * Uses PostgreSQL SERIAL (auto-increment)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * User's email address
     * - Used for login
     * - Must be unique across all users
     * - Validated by email format
     */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * User's hashed password
     * - Stored as BCrypt hash (never plain text)
     * - Not returned in API responses
     * - Minimum 8 characters (enforced at service layer)
     */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    /**
     * User's full name
     * - Displayed in profile
     * - Used in search
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Professional headline (e.g., "Software Engineer at Google")
     * - Displayed under name in profile
     * - Premium users: Required, min 10 characters
     * - Basic users: Optional
     */
    @Column(name = "headline", length = 255)
    private String headline;

    /**
     * Profile summary/bio
     * - Longer description of professional background
     * - Premium users: Required, min 50 characters
     * - Basic users: Optional
     */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /**
     * Location/City
     * - Example: "San Francisco, CA"
     * - Premium users: Required
     * - Basic users: Optional
     */
    @Column(name = "location", length = 255)
    private String location;

    /**
     * URL to profile photo
     * - Stored in S3/Cloud Storage
     * - Only URL stored in database
     * - Premium users: Required
     * - Basic users: Optional
     */
    @Column(name = "profile_photo_url", length = 512)
    private String profilePhotoUrl;

    /**
     * Account type: BASIC (free) or PREMIUM (paid)
     * - Determines which validation strategy to use
     * - Controls access to premium features
     * - Default: BASIC
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    @Builder.Default
    private AccountType accountType = AccountType.BASIC;

    /**
     * User roles for role-based access control (RBAC)
     * - Set of roles (USER, ADMIN)
     * - Stored as JSON array in database: ["USER"] or ["USER", "ADMIN"]
     * - Default: USER role
     * - ElementCollection: Each role is stored separately in database
     * - CollectionTable: Mapped to user_roles join table
     * 
     * Examples:
     * - Regular user: [USER]
     * - Admin user: [USER, ADMIN]
     * 
     * Usage in Spring Security:
     * - @PreAuthorize("hasRole('ADMIN')")
     * - @PreAuthorize("hasRole('USER')")
     * - @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
     * 
     * Why Set instead of List?
     * - Prevents duplicate roles
     * - Order doesn't matter for roles
     * - Efficient contains() check
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<UserRole> roles = new HashSet<>(Set.of(UserRole.USER));

    /**
     * Whether the account is active
     * - false: Account suspended/deactivated
     * - true: Account active (default)
     * - Used for soft delete
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Whether email has been verified
     * - false: Email not verified (default)
     * - true: User clicked verification link
     * - Can restrict features until verified
     */
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    /**
     * Phone number (optional)
     * - For 2FA, notifications
     * - Format validated at service layer
     */
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    /**
     * Date of birth
     * - Used for age verification
     * - Privacy: Not shown publicly by default
     * - Optional field
     */
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * Industry/field (e.g., "Technology", "Healthcare")
     * - Used for job recommendations
     * - Used in search filters
     */
    @Column(name = "industry", length = 100)
    private String industry;

    /**
     * Current job title
     * - Example: "Senior Software Engineer"
     * - Displayed in profile
     */
    @Column(name = "current_job_title", length = 255)
    private String currentJobTitle;

    /**
     * Current company name
     * - Example: "Google"
     * - Could be foreign key to Company entity (future)
     */
    @Column(name = "current_company", length = 255)
    private String currentCompany;

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * Checks if user has premium account
     * 
     * @return true if premium, false if basic
     */
    public boolean isPremium() {
        return AccountType.PREMIUM.equals(this.accountType);
    }

    /**
     * Checks if user account is active
     * 
     * @return true if active, false if deactivated
     */
    public boolean isAccountActive() {
        return Boolean.TRUE.equals(this.isActive);
    }

    /**
     * Checks if user has verified their email
     * 
     * @return true if verified, false otherwise
     */
    public boolean hasVerifiedEmail() {
        return Boolean.TRUE.equals(this.emailVerified);
    }

    /**
     * Activates the user account
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * Deactivates the user account (soft delete)
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Marks email as verified
     */
    public void verifyEmail() {
        this.emailVerified = true;
    }

    /**
     * Upgrades account to premium
     */
    public void upgradeToPremium() {
        this.accountType = AccountType.PREMIUM;
    }

    /**
     * Downgrades account to basic
     */
    public void downgradeToBasic() {
        this.accountType = AccountType.BASIC;
    }

    // ============================================
    // Role Management Helper Methods
    // ============================================

    /**
     * Add a role to this user.
     * 
     * @param role Role to add
     */
    public void addRole(UserRole role) {
        if (this.roles == null) {
            this.roles = new HashSet<>();
        }
        this.roles.add(role);
    }

    /**
     * Remove a role from this user.
     * 
     * @param role Role to remove
     */
    public void removeRole(UserRole role) {
        if (this.roles != null) {
            this.roles.remove(role);
        }
    }

    /**
     * Check if user has a specific role.
     * 
     * @param role Role to check
     * @return true if user has the role
     */
    public boolean hasRole(UserRole role) {
        return this.roles != null && this.roles.contains(role);
    }

    /**
     * Check if user has admin role.
     * 
     * @return true if user is admin
     */
    public boolean isAdmin() {
        return hasRole(UserRole.ADMIN);
    }

    /**
     * Promote user to admin by adding ADMIN role.
     * User keeps USER role as well.
     */
    public void promoteToAdmin() {
        addRole(UserRole.ADMIN);
    }

    /**
     * Demote user from admin by removing ADMIN role.
     * User keeps USER role.
     */
    public void demoteFromAdmin() {
        removeRole(UserRole.ADMIN);
    }

    /**
     * Get all role names as strings.
     * Useful for logging and display.
     * 
     * @return Set of role names
     */
    public Set<String> getRoleNames() {
        if (this.roles == null) {
            return Set.of();
        }
        return this.roles.stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());
    }
}

