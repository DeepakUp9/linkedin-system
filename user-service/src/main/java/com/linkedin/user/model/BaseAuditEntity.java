package com.linkedin.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Base entity class providing audit fields for all entities.
 * 
 * This abstract class provides four audit fields that are automatically
 * populated by Spring Data JPA's auditing mechanism:
 * - createdAt: When the entity was first persisted
 * - updatedAt: When the entity was last modified
 * - createdBy: Username/ID of user who created the entity
 * - updatedBy: Username/ID of user who last modified the entity
 * 
 * Design Pattern: Template Method Pattern
 * - Defines common structure (audit fields) for all entities
 * - Child entities inherit these fields without duplication
 * 
 * JPA Inheritance Strategy:
 * - @MappedSuperclass: Fields are included in child entity tables
 * - Not an entity itself (no separate table)
 * - Each child entity has its own table with these columns
 * 
 * Auditing Mechanism:
 * - @EntityListeners(AuditingEntityListener.class) enables auditing
 * - @CreatedDate, @LastModifiedDate: Managed by JPA
 * - @CreatedBy, @LastModifiedBy: Requires AuditorAware implementation
 * 
 * Benefits:
 * 1. DRY Principle: Define audit fields once
 * 2. Consistency: All entities have same audit structure
 * 3. Automatic: JPA populates fields automatically
 * 4. Compliance: Audit trail for GDPR, SOX requirements
 * 5. Debugging: Easy to track when/who created/modified records
 * 
 * Usage Example:
 * <pre>
 * {@code
 * @Entity
 * @Table(name = "users")
 * public class User extends BaseAuditEntity {
 *     @Id
 *     private Long id;
 *     private String email;
 *     // createdAt, updatedAt inherited automatically
 * }
 * }
 * </pre>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseAuditEntity {

    /**
     * Timestamp when the entity was first created
     * 
     * Automatically set by JPA on INSERT
     * Immutable after creation (updatable = false)
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the entity was last modified
     * 
     * Automatically updated by JPA on UPDATE
     * Set to current time on every modification
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Username or ID of user who created the entity
     * 
     * Automatically populated from SecurityContext
     * Requires AuditorAware<String> bean configuration
     * Immutable after creation (updatable = false)
     */
    @CreatedBy
    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    /**
     * Username or ID of user who last modified the entity
     * 
     * Automatically updated from SecurityContext
     * Requires AuditorAware<String> bean configuration
     * Updated on every modification
     */
    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}

