package com.linkedin.user.mapper;

import com.linkedin.user.dto.CreateUserRequest;
import com.linkedin.user.dto.UpdateUserRequest;
import com.linkedin.user.dto.UserResponse;
import com.linkedin.user.model.User;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for User entity and DTOs.
 * 
 * This interface defines mapping methods between User entity and various DTOs.
 * MapStruct automatically generates implementation code at compile time.
 * 
 * What is MapStruct?
 * 
 * MapStruct is an annotation processor that generates type-safe bean mapping code
 * at compile time. It's a code generator, NOT a runtime reflection-based mapper.
 * 
 * Benefits Over Manual Mapping:
 * 
 * 1. Less Code:
 *    - Manual: 20+ lines per mapping
 *    - MapStruct: 1 line method declaration
 * 
 * 2. Type Safety:
 *    - Compile-time checking of field names and types
 *    - Refactoring support (IDE can rename fields)
 *    - No runtime ClassCastException
 * 
 * 3. Performance:
 *    - Generated code is as fast as hand-written
 *    - No reflection overhead
 *    - JIT-friendly (can be inlined)
 * 
 * 4. Maintainability:
 *    - Add field to entity → MapStruct automatically maps it
 *    - No manual updates needed (if names match)
 *    - Easy to customize with annotations
 * 
 * MapStruct vs Other Mapping Libraries:
 * 
 * ModelMapper (Reflection-based):
 * - ❌ Runtime reflection (slower)
 * - ❌ No compile-time validation
 * - ❌ Hard to debug (magic mapping)
 * - ✅ No code generation needed
 * 
 * Dozer (Reflection-based):
 * - ❌ Runtime reflection (slower)
 * - ❌ XML configuration (verbose)
 * - ❌ No type safety
 * - ❌ Less actively maintained
 * 
 * MapStruct (Code Generation):
 * - ✅ Compile-time code generation (fast)
 * - ✅ Type-safe (compile errors for mismatches)
 * - ✅ Easy to debug (generated code is readable)
 * - ✅ IDE-friendly (refactoring support)
 * - ✅ Actively maintained
 * 
 * How MapStruct Works:
 * 
 * 1. You write interface with mapping methods
 * 2. MapStruct annotation processor runs during compilation
 * 3. Generates implementation class (UserMapperImpl.java)
 * 4. Spring auto-wires generated implementation
 * 
 * Example Generated Code:
 * 
 * Source Interface:
 * <pre>
 * {@code
 * @Mapper(componentModel = "spring")
 * public interface UserMapper {
 *     UserResponse toResponse(User user);
 * }
 * }
 * </pre>
 * 
 * Generated Implementation (UserMapperImpl.java):
 * <pre>
 * {@code
 * @Component
 * public class UserMapperImpl implements UserMapper {
 *     @Override
 *     public UserResponse toResponse(User user) {
 *         if (user == null) {
 *             return null;
 *         }
 *         
 *         UserResponse.UserResponseBuilder response = UserResponse.builder();
 *         response.id(user.getId());
 *         response.email(user.getEmail());
 *         response.name(user.getName());
 *         response.headline(user.getHeadline());
 *         response.summary(user.getSummary());
 *         response.location(user.getLocation());
 *         response.profilePhotoUrl(user.getProfilePhotoUrl());
 *         response.accountType(user.getAccountType());
 *         response.isActive(user.getIsActive());
 *         response.emailVerified(user.getEmailVerified());
 *         response.phoneNumber(user.getPhoneNumber());
 *         response.dateOfBirth(user.getDateOfBirth());
 *         response.industry(user.getIndustry());
 *         response.currentJobTitle(user.getCurrentJobTitle());
 *         response.currentCompany(user.getCurrentCompany());
 *         response.createdAt(user.getCreatedAt());
 *         return response.build();
 *     }
 * }
 * }
 * </pre>
 * 
 * Annotation Explanations:
 * 
 * @Mapper:
 * - Marks interface as MapStruct mapper
 * - Triggers code generation
 * 
 * componentModel = "spring":
 * - Generates @Component annotation on implementation
 * - Makes mapper a Spring bean
 * - Enables dependency injection
 * 
 * @Mapping:
 * - Customizes individual field mapping
 * - Example: @Mapping(target = "fullName", source = "name")
 * - Used when field names don't match
 * 
 * @MappingTarget:
 * - Updates existing object instead of creating new one
 * - Used for PATCH operations (partial updates)
 * 
 * @NullValuePropertyMappingStrategy:
 * - Controls how null values are handled
 * - IGNORE: Don't update field if source is null (for updates)
 * - SET_TO_NULL: Set target to null if source is null
 * 
 * Mapping Strategies:
 * 
 * 1. Automatic Mapping (Same Field Names):
 * <pre>
 * {@code
 * // User entity has: email, name, headline
 * // UserResponse has: email, name, headline
 * // MapStruct automatically maps all three ✅
 * 
 * UserResponse toResponse(User user);
 * }
 * </pre>
 * 
 * 2. Explicit Mapping (Different Field Names):
 * <pre>
 * {@code
 * @Mapping(target = "fullName", source = "name")
 * @Mapping(target = "userEmail", source = "email")
 * UserResponse toResponse(User user);
 * }
 * </pre>
 * 
 * 3. Ignore Fields:
 * <pre>
 * {@code
 * @Mapping(target = "password", ignore = true)
 * User toEntity(CreateUserRequest request);
 * }
 * </pre>
 * 
 * 4. Custom Expressions:
 * <pre>
 * {@code
 * @Mapping(target = "fullName", expression = "java(user.getFirstName() + \" \" + user.getLastName())")
 * UserResponse toResponse(User user);
 * }
 * </pre>
 * 
 * 5. Multiple Sources:
 * <pre>
 * {@code
 * @Mapping(target = "email", source = "user.email")
 * @Mapping(target = "companyName", source = "company.name")
 * UserResponse toResponse(User user, Company company);
 * }
 * </pre>
 * 
 * Null Handling:
 * 
 * By default, MapStruct:
 * - Returns null if source is null
 * - Copies null values from source to target
 * 
 * For updates, use @BeanMapping to ignore nulls:
 * <pre>
 * {@code
 * @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
 * void updateFromDto(UpdateUserRequest dto, @MappingTarget User entity);
 * }
 * </pre>
 * 
 * Collection Mapping:
 * 
 * MapStruct automatically handles collections:
 * <pre>
 * {@code
 * // Single entity mapping
 * UserResponse toResponse(User user);
 * 
 * // List mapping (automatically generated)
 * List<UserResponse> toResponseList(List<User> users);
 * 
 * // Internally calls toResponse() for each element
 * }
 * </pre>
 * 
 * Performance:
 * 
 * MapStruct generated code is:
 * - As fast as hand-written code (no reflection)
 * - JIT-friendly (can be inlined)
 * - Zero runtime overhead
 * 
 * Benchmark comparison (1 million mappings):
 * - Manual mapping: 50ms
 * - MapStruct: 52ms (virtually identical)
 * - ModelMapper: 3,500ms (70x slower)
 * - Dozer: 12,000ms (240x slower)
 * 
 * Usage in Service Layer:
 * 
 * <pre>
 * {@code
 * @Service
 * @RequiredArgsConstructor
 * public class UserService {
 *     private final UserRepository userRepository;
 *     private final UserMapper userMapper;  // Auto-injected by Spring
 *     
 *     public UserResponse getUserById(Long id) {
 *         User user = userRepository.findById(id)
 *             .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
 *         
 *         // One line mapping!
 *         return userMapper.toResponse(user);
 *     }
 *     
 *     public List<UserResponse> getAllUsers() {
 *         List<User> users = userRepository.findAll();
 *         
 *         // Collection mapping!
 *         return userMapper.toResponseList(users);
 *     }
 *     
 *     public UserResponse updateUser(Long id, UpdateUserRequest request) {
 *         User user = userRepository.findById(id).orElseThrow();
 *         
 *         // Update existing entity (PATCH)
 *         userMapper.updateFromDto(request, user);
 *         
 *         User updated = userRepository.save(user);
 *         return userMapper.toResponse(updated);
 *     }
 * }
 * }
 * </pre>
 * 
 * Debugging Generated Code:
 * 
 * To see generated code:
 * 1. Build project: mvn clean compile
 * 2. Check: target/generated-sources/annotations/com/linkedin/user/mapper/UserMapperImpl.java
 * 3. Read the generated implementation
 * 
 * Common Issues and Solutions:
 * 
 * 1. "Unmapped target properties":
 *    - Warning when target has fields not in source
 *    - Solution: Add @Mapping(target = "field", ignore = true)
 *    - Or: componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE
 * 
 * 2. "Can't map List<Entity> to List<DTO>":
 *    - Need to define single entity mapping first
 *    - MapStruct will auto-generate list mapping
 * 
 * 3. "Circular reference error":
 *    - Entity has bidirectional relationships
 *    - Solution: Break cycle with @Mapping(target = "parent", ignore = true)
 * 
 * Best Practices:
 * 
 * 1. One mapper per entity
 *    - UserMapper for User entity
 *    - PostMapper for Post entity
 *    - Clear separation of concerns
 * 
 * 2. Keep mappers simple
 *    - Complex logic belongs in service layer
 *    - Mappers should only map data
 * 
 * 3. Use @MappingTarget for updates
 *    - Efficient (updates existing object)
 *    - Works with JPA dirty checking
 *    - Preserves audit fields
 * 
 * 4. Define collection mappings explicitly
 *    - Makes intent clear
 *    - Better IDE support
 * 
 * 5. Test generated mappings
 *    - Verify all fields are mapped correctly
 *    - Check null handling
 *    - Test edge cases
 * 
 * @see User
 * @see UserResponse
 * @see CreateUserRequest
 * @see UpdateUserRequest
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Converts User entity to UserResponse DTO.
     * 
     * This is the most common mapping - from database entity to API response.
     * 
     * Mapping Details:
     * - All matching field names are automatically mapped
     * - password field in User is NOT in UserResponse → automatically excluded
     * - updatedAt, createdBy, updatedBy in User → NOT in UserResponse → excluded
     * - All other fields with matching names → automatically copied
     * 
     * Null Handling:
     * - If user is null → returns null
     * - If any user field is null → corresponding response field is null
     * - @JsonInclude(NON_NULL) in UserResponse will exclude nulls from JSON
     * 
     * Generated Code Example:
     * <pre>
     * {@code
     * public UserResponse toResponse(User user) {
     *     if (user == null) return null;
     *     
     *     return UserResponse.builder()
     *         .id(user.getId())
     *         .email(user.getEmail())
     *         .name(user.getName())
     *         .headline(user.getHeadline())
     *         .summary(user.getSummary())
     *         .location(user.getLocation())
     *         .profilePhotoUrl(user.getProfilePhotoUrl())
     *         .accountType(user.getAccountType())
     *         .isActive(user.getIsActive())
     *         .emailVerified(user.getEmailVerified())
     *         .phoneNumber(user.getPhoneNumber())
     *         .dateOfBirth(user.getDateOfBirth())
     *         .industry(user.getIndustry())
     *         .currentJobTitle(user.getCurrentJobTitle())
     *         .currentCompany(user.getCurrentCompany())
     *         .createdAt(user.getCreatedAt())
     *         .build();
     * }
     * }
     * </pre>
     * 
     * Usage:
     * <pre>
     * {@code
     * User user = userRepository.findById(1L).orElseThrow();
     * UserResponse response = userMapper.toResponse(user);
     * }
     * </pre>
     * 
     * @param user User entity from database
     * @return UserResponse DTO for API response (null if user is null)
     */
    UserResponse toResponse(User user);

    /**
     * Converts list of User entities to list of UserResponse DTOs.
     * 
     * MapStruct automatically generates this implementation by calling
     * toResponse() for each element in the list.
     * 
     * Null Handling:
     * - If users list is null → returns null
     * - If list contains null elements → result list contains nulls
     * - Empty list → returns empty list
     * 
     * Generated Code Example:
     * <pre>
     * {@code
     * public List<UserResponse> toResponseList(List<User> users) {
     *     if (users == null) return null;
     *     
     *     List<UserResponse> list = new ArrayList<>(users.size());
     *     for (User user : users) {
     *         list.add(toResponse(user));  // Calls single entity mapping
     *     }
     *     return list;
     * }
     * }
     * </pre>
     * 
     * Usage:
     * <pre>
     * {@code
     * List<User> users = userRepository.findAll();
     * List<UserResponse> responses = userMapper.toResponseList(users);
     * }
     * </pre>
     * 
     * Performance:
     * - O(n) time complexity
     * - No reflection overhead
     * - JIT-friendly loop
     * 
     * @param users List of User entities
     * @return List of UserResponse DTOs
     */
    List<UserResponse> toResponseList(List<User> users);

    /**
     * Updates existing User entity from UpdateUserRequest DTO.
     * 
     * This is used for PATCH operations where we want to update only
     * the fields provided in the request, leaving others unchanged.
     * 
     * Key Feature: @MappingTarget
     * - Updates existing object instead of creating new one
     * - Efficient (no object creation overhead)
     * - Works with JPA dirty checking (only changed fields updated in DB)
     * - Preserves fields not in DTO (like createdAt, createdBy)
     * 
     * Null Value Handling:
     * - NullValuePropertyMappingStrategy.IGNORE
     * - If DTO field is null → entity field is NOT updated
     * - Only non-null DTO fields update entity
     * 
     * Ignored Fields:
     * - id: Primary key, never updated
     * - email: Identity field, updated separately (requires verification)
     * - password: Updated separately (requires old password validation)
     * - accountType: Updated separately (business logic for upgrades)
     * - isActive: Updated separately (admin/system operation)
     * - emailVerified: Updated separately (verification flow)
     * - createdAt, updatedAt: Managed by JPA auditing
     * - createdBy, updatedBy: Managed by JPA auditing
     * 
     * Example:
     * <pre>
     * {@code
     * // Existing user in database:
     * User user = {
     *     id: 1,
     *     email: "john@example.com",
     *     name: "John Doe",
     *     headline: "Developer",
     *     summary: "Experienced engineer",
     *     location: "San Francisco"
     * }
     * 
     * // Update request (only changing name and headline):
     * UpdateUserRequest request = {
     *     name: "John Smith",
     *     headline: "Senior Developer",
     *     summary: null,      // Not provided
     *     location: null      // Not provided
     * }
     * 
     * // After updateFromDto(request, user):
     * user = {
     *     id: 1,
     *     email: "john@example.com",
     *     name: "John Smith",           // ← Updated
     *     headline: "Senior Developer",  // ← Updated
     *     summary: "Experienced engineer", // ← Unchanged (DTO was null)
     *     location: "San Francisco"      // ← Unchanged (DTO was null)
     * }
     * }
     * </pre>
     * 
     * Generated Code Example:
     * <pre>
     * {@code
     * public void updateFromDto(UpdateUserRequest dto, User user) {
     *     if (dto == null) return;
     *     
     *     if (dto.getName() != null) {
     *         user.setName(dto.getName());
     *     }
     *     if (dto.getHeadline() != null) {
     *         user.setHeadline(dto.getHeadline());
     *     }
     *     if (dto.getSummary() != null) {
     *         user.setSummary(dto.getSummary());
     *     }
     *     // ... for all other fields
     * }
     * }
     * </pre>
     * 
     * Usage:
     * <pre>
     * {@code
     * User user = userRepository.findById(1L).orElseThrow();
     * UpdateUserRequest request = getUpdateRequest();
     * 
     * // Updates user object in-place
     * userMapper.updateFromDto(request, user);
     * 
     * // Save (JPA only updates changed fields)
     * userRepository.save(user);
     * }
     * </pre>
     * 
     * Why This is Better Than Creating New Entity:
     * 
     * Option 1 (Bad - Overwrites everything):
     * <pre>
     * {@code
     * User user = new User();
     * user.setName(request.getName());
     * user.setHeadline(request.getHeadline());
     * // Problem: What about fields not in request?
     * // Lost: createdAt, createdBy, etc.
     * }
     * </pre>
     * 
     * Option 2 (Good - Updates existing):
     * <pre>
     * {@code
     * User user = userRepository.findById(id).orElseThrow();
     * userMapper.updateFromDto(request, user);
     * // Benefit: Preserves all existing fields
     * // Only updates what's in request
     * }
     * </pre>
     * 
     * @param dto UpdateUserRequest containing fields to update
     * @param user Existing User entity to be updated (modified in-place)
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "accountType", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateFromDto(UpdateUserRequest dto, @MappingTarget User user);
}

