package com.linkedin.user.repository;

import com.linkedin.user.model.AccountType;
import com.linkedin.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for User entity.
 * 
 * This interface extends JpaRepository which provides:
 * 1. Standard CRUD operations (save, findById, findAll, delete, etc.)
 * 2. Pagination and sorting support
 * 3. Batch operations
 * 4. Query derivation from method names
 * 5. Transaction management
 * 
 * Spring Data JPA automatically generates implementations at runtime.
 * No implementation class needed - just declare method signatures!
 * 
 * Method Naming Convention:
 * - findBy<FieldName>: SELECT query
 * - existsBy<FieldName>: Check existence
 * - countBy<FieldName>: Count matching records
 * - deleteBy<FieldName>: Delete matching records
 * 
 * Examples:
 * - findByEmail → SELECT * FROM users WHERE email = ?
 * - findByAccountType → SELECT * FROM users WHERE account_type = ?
 * - existsByEmail → SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)
 * 
 * Inherited Methods (from JpaRepository):
 * - save(User user): Insert or update
 * - findById(Long id): Find by primary key
 * - findAll(): Get all users
 * - delete(User user): Delete user
 * - count(): Count total users
 * - And many more...
 * 
 * @see JpaRepository
 * @see User
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email address
     * 
     * Spring Data JPA generates:
     * SELECT * FROM users WHERE email = ?
     * 
     * @param email Email address to search for
     * @return Optional containing user if found, empty otherwise
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if user exists with given email
     * 
     * Spring Data JPA generates:
     * SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)
     * 
     * More efficient than findByEmail when you only need to check existence
     * 
     * @param email Email address to check
     * @return true if user exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Find all users by account type
     * 
     * Spring Data JPA generates:
     * SELECT * FROM users WHERE account_type = ?
     * 
     * @param accountType Account type to filter by (BASIC or PREMIUM)
     * @return List of users with given account type
     */
    List<User> findByAccountType(AccountType accountType);

    /**
     * Find all users by account type with pagination
     * 
     * Supports pagination and sorting for large result sets
     * 
     * @param accountType Account type to filter by
     * @param pageable Pagination and sorting parameters
     * @return Page of users
     */
    Page<User> findByAccountType(AccountType accountType, Pageable pageable);

    /**
     * Find all active users
     * 
     * Spring Data JPA generates:
     * SELECT * FROM users WHERE is_active = true
     * 
     * @return List of active users
     */
    List<User> findByIsActiveTrue();

    /**
     * Find users by name containing search term (case-insensitive)
     * 
     * Spring Data JPA generates:
     * SELECT * FROM users WHERE LOWER(name) LIKE LOWER(?)
     * 
     * @param name Search term to match in user names
     * @return List of users whose name contains the search term
     */
    List<User> findByNameContainingIgnoreCase(String name);

    /**
     * Find all premium users who have verified their email
     * 
     * Spring Data JPA generates:
     * SELECT * FROM users 
     * WHERE account_type = 'PREMIUM' AND email_verified = true
     * 
     * @return List of verified premium users
     */
    List<User> findByAccountTypeAndEmailVerifiedTrue(AccountType accountType);

    /**
     * Count users by account type
     * 
     * Spring Data JPA generates:
     * SELECT COUNT(*) FROM users WHERE account_type = ?
     * 
     * @param accountType Account type to count
     * @return Number of users with given account type
     */
    long countByAccountType(AccountType accountType);

    /**
     * Custom JPQL query: Find users created after a specific date
     * 
     * Uses @Query annotation for complex queries that can't be
     * expressed with method naming convention
     * 
     * @param date The date to filter by
     * @return List of users created after the specified date
     */
    @Query("SELECT u FROM User u WHERE u.createdAt > :date AND u.isActive = true")
    List<User> findActiveUsersCreatedAfter(@Param("date") java.time.LocalDateTime date);

    /**
     * Custom JPQL query: Search users by multiple criteria
     * 
     * Demonstrates more complex query with multiple conditions
     * 
     * @param searchTerm Term to search in name, email, or headline
     * @param accountType Account type filter
     * @param pageable Pagination parameters
     * @return Page of matching users
     */
    @Query("SELECT u FROM User u WHERE " +
           "(LOWER(u.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.headline) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "u.accountType = :accountType AND " +
           "u.isActive = true")
    Page<User> searchActiveUsersByAccountType(
        @Param("searchTerm") String searchTerm,
        @Param("accountType") AccountType accountType,
        Pageable pageable
    );

    /**
     * Custom native SQL query: Get user statistics by account type
     * 
     * Uses native SQL instead of JPQL when needed
     * 
     * @return List of Object arrays containing [account_type, count]
     */
    @Query(value = "SELECT account_type, COUNT(*) as user_count " +
                   "FROM users " +
                   "WHERE is_active = true " +
                   "GROUP BY account_type",
           nativeQuery = true)
    List<Object[]> getUserStatisticsByAccountType();
}

