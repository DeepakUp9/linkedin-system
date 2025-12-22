# Learning Guide - Design Patterns & Best Practices

This guide explains the **WHY** behind every architectural decision and design pattern used in this project.

---

## 🎯 Table of Contents
1. [Layered Architecture](#layered-architecture)
2. [Factory Pattern](#factory-pattern)
3. [Strategy Pattern](#strategy-pattern)
4. [Repository Pattern](#repository-pattern)
5. [DTO Pattern](#dto-pattern)
6. [Dependency Injection](#dependency-injection)
7. [Transaction Management](#transaction-management)
8. [Exception Handling](#exception-handling)
9. [Security Best Practices](#security-best-practices)
10. [Testing Strategy](#testing-strategy)

---

## 🏗️ Layered Architecture

### What is it?
Separating your application into distinct layers, each with a specific responsibility.

### Our Layers
```
┌──────────────────────────┐
│   Presentation Layer     │ ← HTTP, JSON, validation
│   (Controllers)          │
└──────────────────────────┘
           │
┌──────────────────────────┐
│   Service Layer          │ ← Business logic
│   (Services)             │
└──────────────────────────┘
           │
┌──────────────────────────┐
│   Data Layer             │ ← Database queries
│   (Repositories)         │
└──────────────────────────┘
           │
┌──────────────────────────┐
│   Database               │ ← PostgreSQL
└──────────────────────────┘
```

### Why?
**Problem**: Mixing HTTP handling, business logic, and database queries makes code:
- Hard to test (can't test business logic without HTTP server)
- Hard to change (changing database affects HTTP layer)
- Hard to understand (one class does too many things)

**Solution**: Each layer has ONE responsibility:
- **Controller**: Handle HTTP, validate input, format responses
- **Service**: Orchestrate business logic, enforce rules
- **Repository**: Execute database queries

### Example
❌ **Bad** (everything in controller):
```java
@RestController
public class UserController {
    @Autowired
    private EntityManager em;
    
    @PostMapping("/users")
    public User createUser(@RequestBody CreateUserRequest request) {
        // Validation in controller
        if (request.getEmail() == null) {
            throw new RuntimeException("Email required");
        }
        
        // Password hashing in controller
        String hashedPassword = BCrypt.hashpw(request.getPassword(), BCrypt.gensalt());
        
        // Database query in controller
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(hashedPassword);
        em.persist(user);
        
        return user; // Exposing entity directly
    }
}
// 🚨 Problems:
// - Can't test validation without starting HTTP server
// - Can't reuse logic in other endpoints
// - Hard to add caching, logging, transactions
```

✅ **Good** (layered):
```java
@RestController
public class UserController {
    @Autowired
    private UserService userService;
    
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
        @Valid @RequestBody CreateUserRequest request
    ) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }
}
// ✅ Benefits:
// - Controller only handles HTTP
// - Business logic in service (testable independently)
// - Can add transactions, caching at service level
```

---

## 🏭 Factory Pattern

### What is it?
Centralized place for creating objects with complex initialization.

### Our Implementation
- **Interface**: `UserFactory`
- **Implementation**: `UserFactoryImpl`
- **Purpose**: Create `User` entities with proper initialization

### Why?
**Problem**: Creating a user requires many steps:
1. Validate email format
2. Validate password strength
3. Hash password with BCrypt
4. Set default values (isActive, emailVerified)
5. Set account type
6. Validate PREMIUM requirements

Doing this everywhere leads to:
- Duplicate code (validation in 5 places)
- Inconsistency (forgot to hash password in one place)
- Hard to maintain (change validation rules → update 5 files)

**Solution**: One factory class handles all object creation logic.

### Example
❌ **Bad** (manual creation everywhere):
```java
// In UserService
User user = new User();
user.setEmail(request.getEmail());
user.setPassword(passwordEncoder.encode(request.getPassword())); // What if you forget?
user.setAccountType(AccountType.BASIC);
user.setIsActive(true);
user.setEmailVerified(false);

// In AdminService (duplicate code!)
User user = new User();
user.setEmail(request.getEmail());
user.setPassword(passwordEncoder.encode(request.getPassword()));
user.setAccountType(AccountType.BASIC);
user.setIsActive(true);
user.setEmailVerified(false); // Duplicated 😱
```

✅ **Good** (factory):
```java
// In UserService
User user = userFactory.createUser(request);

// In AdminService
User user = userFactory.createUser(request);

// UserFactoryImpl (one place for all logic)
public User createUser(CreateUserRequest request) {
    validateEmail(request.getEmail());
    validatePassword(request.getPassword());
    
    User user = userMapper.toEntity(request);
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    user.setAccountType(AccountType.BASIC);
    user.setIsActive(true);
    user.setEmailVerified(false);
    
    return user;
}
// ✅ Single source of truth!
```

### When to Use Factory Pattern?
- ✅ Object creation requires multiple steps
- ✅ Object creation has validation logic
- ✅ Need to create objects in multiple places
- ✅ Object initialization might change frequently

---

## 🎯 Strategy Pattern

### What is it?
Define a family of algorithms (strategies), encapsulate each one, and make them interchangeable.

### Our Implementation
- **Interface**: `ProfileValidationStrategy`
- **Strategies**: `BasicProfileValidationStrategy`, `PremiumProfileValidationStrategy`
- **Context**: `ValidationStrategyFactory`

### Why?
**Problem**: Different account types have different validation rules:
- BASIC: Only email, password, name required
- PREMIUM: Email, password, name, headline, summary, location required
- ENTERPRISE (future): All above + company info

Without Strategy Pattern, you'd have:
```java
public void validateProfile(User user) {
    if (user.getAccountType() == AccountType.BASIC) {
        // 20 lines of validation
    } else if (user.getAccountType() == AccountType.PREMIUM) {
        // 50 lines of validation
    } else if (user.getAccountType() == AccountType.ENTERPRISE) {
        // 100 lines of validation
    }
    // 🚨 This method becomes HUGE and unmaintainable!
}
```

**Solution**: Each account type gets its own strategy class.

### Example
❌ **Bad** (if-else hell):
```java
public void validateProfile(User user) {
    validateEmail(user);
    validatePassword(user);
    validateName(user);
    
    if (user.getAccountType() == AccountType.PREMIUM) {
        if (user.getHeadline() == null || user.getHeadline().length() < 10) {
            throw new ValidationException("Headline required");
        }
        if (user.getSummary() == null || user.getSummary().length() < 50) {
            throw new ValidationException("Summary required");
        }
        if (user.getLocation() == null || user.getLocation().length() < 3) {
            throw new ValidationException("Location required");
        }
    } else if (user.getAccountType() == AccountType.ENTERPRISE) {
        // Another 50 lines...
    }
    // 🚨 Adding new account type requires modifying this method!
}
```

✅ **Good** (strategy):
```java
// Service layer (clean!)
ProfileValidationStrategy strategy = strategyFactory.getStrategy(user.getAccountType());
strategy.validate(user);

// BasicProfileValidationStrategy.java (focused!)
public void validate(User user) {
    validateEmail(user);
    validatePassword(user);
    validateName(user);
    // Only BASIC logic here
}

// PremiumProfileValidationStrategy.java (focused!)
public void validate(User user) {
    validateEmail(user);
    validatePassword(user);
    validateName(user);
    validateHeadline(user);  // PREMIUM-specific
    validateSummary(user);   // PREMIUM-specific
    validateLocation(user);  // PREMIUM-specific
}

// Adding ENTERPRISE? Create EnterpriseProfileValidationStrategy.java
// 🎉 No changes to existing code! (Open/Closed Principle)
```

### When to Use Strategy Pattern?
- ✅ Behavior varies based on a type (account type, user role, payment method)
- ✅ Multiple if-else or switch statements based on type
- ✅ Want to add new behaviors without changing existing code
- ✅ Each behavior is complex enough to deserve its own class

---

## 🗄️ Repository Pattern

### What is it?
Abstract database operations behind an interface.

### Our Implementation
- **Interface**: `UserRepository extends JpaRepository<User, Long>`
- **Implementation**: Auto-generated by Spring Data JPA

### Why?
**Problem**: Mixing SQL queries with business logic:
```java
public UserResponse createUser(CreateUserRequest request) {
    // Business logic
    User user = new User();
    user.setEmail(request.getEmail());
    
    // SQL query mixed with business logic 😱
    EntityManager em = entityManagerFactory.createEntityManager();
    EntityTransaction tx = em.getTransaction();
    tx.begin();
    em.persist(user);
    tx.commit();
    em.close();
    
    // More business logic
    sendWelcomeEmail(user);
}
```

**Solution**: Repository abstracts database operations.

### Example
❌ **Bad** (SQL in service):
```java
@Service
public class UserService {
    @Autowired
    private EntityManager em;
    
    public User getUserByEmail(String email) {
        Query query = em.createQuery("SELECT u FROM User u WHERE u.email = :email");
        query.setParameter("email", email);
        return (User) query.getSingleResult();
        // 🚨 What if query fails? What if no results? What about closing connection?
    }
}
```

✅ **Good** (repository):
```java
// Repository interface
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    // Spring Data JPA auto-implements this! 🎉
}

// Service
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
```

### Benefits
- ✅ No SQL in service layer (clean separation)
- ✅ Easy to test (mock repository)
- ✅ Easy to switch databases (PostgreSQL → MySQL)
- ✅ Automatic transaction management
- ✅ Built-in pagination, sorting

---

## 📦 DTO Pattern

### What is it?
Data Transfer Objects - objects specifically designed for transferring data between layers.

### Our Implementation
- **Request DTOs**: `CreateUserRequest`, `UpdateUserRequest`
- **Response DTOs**: `UserResponse`
- **Entity**: `User` (never exposed directly)

### Why?
**Problem**: Exposing entities directly to clients:
```java
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).orElseThrow();
}

// Response includes EVERYTHING:
{
  "id": 1,
  "email": "john@example.com",
  "password": "$2a$10$...",  // 🚨 SECURITY ISSUE!
  "createdBy": "admin",      // 🚨 Internal detail
  "updatedAt": "...",        // 🚨 Too much info
  "accountType": "BASIC"
}
```

**Solution**: Return DTOs that only include safe fields.

### Example
❌ **Bad** (entity exposure):
```java
@Entity
public class User {
    private Long id;
    private String email;
    private String password;  // BCrypt hash
    private String createdBy;
    private LocalDateTime updatedAt;
    // All fields exposed to client! 😱
}

@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userService.getUserById(id);
}
```

✅ **Good** (DTO):
```java
public class UserResponse {
    private Long id;
    private String email;
    private String name;
    private AccountType accountType;
    private LocalDateTime createdAt;
    // Only safe fields! ✅
    // No password, no audit fields
}

@GetMapping("/{id}")
public UserResponse getUser(@PathVariable Long id) {
    return userService.getUserById(id);  // Returns UserResponse
}
```

### Benefits
- ✅ **Security**: Never expose passwords, internal IDs
- ✅ **Versioning**: Create `UserResponseV2` without breaking `UserResponseV1`
- ✅ **Clean API**: Only relevant fields in response
- ✅ **Decoupling**: Entity changes don't affect API

---

## 💉 Dependency Injection

### What is it?
Objects don't create their dependencies; dependencies are "injected" by Spring.

### Our Implementation
```java
@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserFactory userFactory;
    
    // Constructor injection (recommended)
    public UserService(UserRepository userRepository, UserFactory userFactory) {
        this.userRepository = userRepository;
        this.userFactory = userFactory;
    }
}
```

### Why?
**Problem**: Creating dependencies manually:
```java
public class UserService {
    private UserRepository userRepository = new UserRepository(); // ❌ Tightly coupled!
    
    public void createUser(CreateUserRequest request) {
        userRepository.save(user);
    }
}
// 🚨 Problems:
// - Can't test (can't replace UserRepository with mock)
// - Can't change implementation
// - Hard to manage dependencies (what if UserRepository needs dependencies?)
```

**Solution**: Let Spring inject dependencies.

### Example
❌ **Bad** (manual creation):
```java
public class UserService {
    private UserRepository userRepository = new UserRepository();
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    // 🚨 Can't mock these for testing!
}
```

✅ **Good** (dependency injection):
```java
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    // Constructor injection
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
}

// In tests:
@Test
void testCreateUser() {
    UserRepository mockRepo = mock(UserRepository.class);
    PasswordEncoder mockEncoder = mock(PasswordEncoder.class);
    UserService service = new UserService(mockRepo, mockEncoder);
    // ✅ Easy to test!
}
```

### Benefits
- ✅ **Testability**: Inject mocks for testing
- ✅ **Flexibility**: Change implementations without code changes
- ✅ **Lifecycle Management**: Spring manages creation and destruction

---

## 🔄 Transaction Management

### What is it?
Ensure database operations are atomic (all-or-nothing).

### Our Implementation
```java
@Service
@Transactional(readOnly = true)  // Default
public class UserService {
    
    @Transactional  // Override for write operations
    public UserResponse createUser(CreateUserRequest request) {
        // Operation 1: Check email
        userRepository.existsByEmail(email);
        
        // Operation 2: Save user
        userRepository.save(user);
        
        // If ANY operation fails, ENTIRE transaction rolls back
    }
}
```

### Why?
**Problem**: Partial updates leave database inconsistent:
```java
public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
    User from = userRepository.findById(fromId).orElseThrow();
    User to = userRepository.findById(toId).orElseThrow();
    
    from.setBalance(from.getBalance().subtract(amount));
    userRepository.save(from);  // ✅ Succeeds
    
    // 💥 Exception here!
    to.setBalance(to.getBalance().add(amount));
    userRepository.save(to);  // ❌ Never executes
    
    // 🚨 Money deducted from 'from' but never added to 'to'! Database inconsistent!
}
```

**Solution**: Wrap in transaction.

### Example
❌ **Bad** (no transaction):
```java
public void createUser(CreateUserRequest request) {
    if (userRepository.existsByEmail(email)) {
        throw new ValidationException("Email exists");
    }
    
    User user = userFactory.createUser(request);
    userRepository.save(user);
    
    // 💥 Exception here!
    auditLogRepository.save(new AuditLog("User created"));
    
    // 🚨 User saved but audit log not saved! Inconsistent!
}
```

✅ **Good** (transaction):
```java
@Transactional
public void createUser(CreateUserRequest request) {
    if (userRepository.existsByEmail(email)) {
        throw new ValidationException("Email exists");
    }
    
    User user = userFactory.createUser(request);
    userRepository.save(user);
    
    // 💥 Exception here!
    auditLogRepository.save(new AuditLog("User created"));
    
    // ✅ Entire transaction rolls back! No user saved, no audit log saved.
    // Database remains consistent!
}
```

### Benefits
- ✅ **Atomicity**: All operations succeed or all fail
- ✅ **Consistency**: Database never in inconsistent state
- ✅ **Automatic Rollback**: Spring handles rollback on exceptions

---

## 🚨 Exception Handling

### What is it?
Centralized place to catch exceptions and return consistent error responses.

### Our Implementation
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handle(ValidationException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .build());
    }
}
```

### Why?
**Problem**: Returning inconsistent error responses:
```java
@PostMapping("/users")
public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
    try {
        return ResponseEntity.ok(userService.createUser(request));
    } catch (ValidationException e) {
        return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }
}

@GetMapping("/{id}")
public ResponseEntity<?> getUser(@PathVariable Long id) {
    try {
        return ResponseEntity.ok(userService.getUserById(id));
    } catch (ResourceNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
}
// 🚨 Different error formats! Client can't parse consistently!
```

**Solution**: Centralized exception handler.

### Example
❌ **Bad** (inconsistent):
```java
@PostMapping("/users")
public ResponseEntity<?> createUser() {
    try {
        // ...
    } catch (ValidationException e) {
        return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        // Format: "Error: ..."
    }
}

@GetMapping("/{id}")
public ResponseEntity<?> getUser() {
    try {
        // ...
    } catch (ResourceNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        // Format: {"error": "..."}
    }
}
// 🚨 Two different error formats!
```

✅ **Good** (centralized):
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handle(ValidationException ex) {
        return buildErrorResponse(ex);
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(ResourceNotFoundException ex) {
        return buildErrorResponse(ex);
    }
}

// All errors have same format:
{
  "errorCode": "USER_EMAIL_EXISTS",
  "message": "Email already registered",
  "timestamp": "2025-12-20T19:30:00",
  "status": 400,
  "path": "/api/users"
}
// ✅ Consistent format! Easy for clients to parse!
```

### Benefits
- ✅ **Consistency**: All errors have same format
- ✅ **DRY**: Don't repeat try-catch in every controller
- ✅ **Clean Controllers**: Controllers focus on happy path

---

## 🔐 Security Best Practices

### 1. Password Hashing (BCrypt)
❌ **Bad**:
```java
user.setPassword(request.getPassword());  // Plain text! 😱
```

✅ **Good**:
```java
user.setPassword(passwordEncoder.encode(request.getPassword()));
// BCrypt: $2a$10$N9qo8uLOickgx2ZMRZoMye... (60 chars)
```

### 2. Never Expose Passwords
❌ **Bad**:
```java
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id).orElseThrow();
    // Response includes password hash! 😱
}
```

✅ **Good**:
```java
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable Long id) {
    return userService.getUserById(id);
    // UserResponse doesn't have password field ✅
}
```

### 3. Input Validation
❌ **Bad**:
```java
@PostMapping("/users")
public UserResponse createUser(@RequestBody CreateUserRequest request) {
    return userService.createUser(request);
    // No validation! ❌
}
```

✅ **Good**:
```java
@PostMapping("/users")
public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
    return userService.createUser(request);
    // @Valid triggers Bean Validation ✅
}

public class CreateUserRequest {
    @NotNull(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
    
    @NotNull(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}
```

---

## 🧪 Testing Strategy

### Unit Tests
Test individual components in isolation.

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private UserFactory userFactory;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void createUser_shouldReturnUserResponse() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");
        
        User user = new User();
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userFactory.createUser(any())).thenReturn(user);
        when(userRepository.save(any())).thenReturn(user);
        
        // Act
        UserResponse response = userService.createUser(request);
        
        // Assert
        assertNotNull(response);
        verify(userRepository).save(any(User.class));
    }
}
```

### Integration Tests
Test entire flow (controller → service → database).

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void createUser_shouldReturn201() throws Exception {
        mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"test@example.com\",\"password\":\"Pass123\",\"name\":\"Test\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }
}
```

---

## 🎓 Summary: When to Use Each Pattern

| Pattern | Use When | Benefits |
|---------|----------|----------|
| **Factory** | Object creation is complex | Centralized creation logic |
| **Strategy** | Behavior varies by type | Easy to add new behaviors |
| **Repository** | Need to abstract database | Testable, database-independent |
| **DTO** | Exposing data to clients | Security, clean API contracts |

---

*This guide explains the reasoning behind every architectural decision. Understanding the WHY helps you apply these patterns to your own projects!* 🚀

