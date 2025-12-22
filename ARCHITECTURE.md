# Architecture Deep Dive - Request Flow & Component Interactions

## 📊 Complete Request Flow

This document explains **how a single API request flows through all layers** of the system, showing exactly how components interact.

---

## 🔄 Example: Creating a PREMIUM User

Let's trace a complete request from **HTTP call → Database → Response**.

### **Initial Request**
```http
POST http://localhost:8080/api/users/premium
Content-Type: application/json

{
  "email": "jane@example.com",
  "password": "SecurePass456",
  "name": "Jane Smith",
  "headline": "Senior Software Engineer",
  "summary": "10 years of experience in cloud architecture and distributed systems...",
  "location": "San Francisco, CA"
}
```

---

## 🎯 Flow Diagram

```
HTTP Request
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  LAYER 1: PRESENTATION (Controller)                         │
│  📁 UserController.java                                     │
│                                                              │
│  @PostMapping("/premium")                                   │
│  public ResponseEntity<ApiResponse<UserResponse>>           │
│      createPremiumUser(@Valid @RequestBody request) {       │
│                                                              │
│    // 1. Bean Validation runs automatically                │
│    //    - @NotNull, @Email, @Size, etc.                   │
│    //    - If fails → MethodArgumentNotValidException       │
│                                                              │
│    // 2. Delegate to service layer                         │
│    UserResponse response = userService.createPremiumUser(   │
│        request                                              │
│    );                                                        │
│                                                              │
│    // 3. Wrap in ApiResponse and return                    │
│    return ResponseEntity                                    │
│        .status(HttpStatus.CREATED)                          │
│        .body(ApiResponse.success(response, "User created"));│
│  }                                                           │
└─────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  LAYER 2: SERVICE (Business Logic)                          │
│  📁 UserService.java                                        │
│                                                              │
│  @Transactional                                             │
│  public UserResponse createPremiumUser(request) {           │
│                                                              │
│    // STEP 1: Validate email uniqueness                    │
│    if (userRepository.existsByEmail(request.getEmail())) {  │
│        throw new ValidationException(                       │
│            "Email already exists",                          │
│            "USER_EMAIL_EXISTS"                              │
│        );                                                    │
│    }                                                         │
│    //    ↓ Query sent to database:                         │
│    //    SELECT COUNT(*) FROM users WHERE email = ?         │
│                                                              │
│    // STEP 2: Create user entity using Factory Pattern     │
│    User user = userFactory.createPremiumUser(request);      │
│    //    ↓ Calls UserFactoryImpl...                        │
└─────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  PATTERN 1: FACTORY (Object Creation)                       │
│  📁 UserFactoryImpl.java                                    │
│                                                              │
│  @Override                                                  │
│  public User createPremiumUser(CreateUserRequest request) { │
│                                                              │
│    // Validate email format                                │
│    validateEmail(request.getEmail());                       │
│    //    Pattern: ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\...    │
│                                                              │
│    // Validate password strength                            │
│    validatePassword(request.getPassword());                 │
│    //    Min 8 chars, uppercase, lowercase, digit           │
│                                                              │
│    // Validate premium-specific requirements               │
│    validatePremiumRequirements(request);                    │
│    //    - Headline min 10 characters                      │
│    //    - Summary min 50 characters                       │
│    //    - Location min 3 characters                       │
│                                                              │
│    // Create user entity                                   │
│    User user = userMapper.toEntity(request);                │
│    //    MapStruct converts DTO → Entity                   │
│                                                              │
│    // Hash password using BCrypt                           │
│    String hashedPassword = passwordEncoder.encode(          │
│        request.getPassword()                                │
│    );                                                        │
│    user.setPassword(hashedPassword);                        │
│    //    BCrypt with salt, work factor 10                  │
│    //    Example: $2a$10$abcd...xyz (60 chars)             │
│                                                              │
│    // Set account type                                     │
│    user.setAccountType(AccountType.PREMIUM);                │
│                                                              │
│    // Set defaults                                         │
│    user.setIsActive(true);                                  │
│    user.setEmailVerified(false);                            │
│                                                              │
│    return user; // Returns to UserService                  │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘
     │
     ▼ (Back to UserService)
┌─────────────────────────────────────────────────────────────┐
│  LAYER 2: SERVICE (continued)                               │
│                                                              │
│    // STEP 3: Validate profile using Strategy Pattern      │
│    ProfileValidationStrategy strategy =                     │
│        validationStrategyFactory.getStrategy(               │
│            user.getAccountType() // PREMIUM                 │
│        );                                                    │
│    //    ↓ Calls ValidationStrategyFactory...              │
└─────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  PATTERN 2: STRATEGY (Behavior Selection)                   │
│  📁 ValidationStrategyFactory.java                          │
│                                                              │
│  public ProfileValidationStrategy getStrategy(              │
│      AccountType accountType                                │
│  ) {                                                         │
│                                                              │
│    // Lookup in EnumMap                                    │
│    //   BASIC   → BasicProfileValidationStrategy           │
│    //   PREMIUM → PremiumProfileValidationStrategy         │
│                                                              │
│    return strategies.get(accountType);                      │
│    //    ↓ Returns PremiumProfileValidationStrategy        │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  PATTERN 2: STRATEGY (continued)                            │
│  📁 PremiumProfileValidationStrategy.java                   │
│                                                              │
│  @Override                                                  │
│  public void validate(User user) {                          │
│                                                              │
│    // Validate all BASIC requirements                      │
│    validateEmail(user);                                     │
│    validatePassword(user);                                  │
│    validateName(user);  // 2-255 characters                │
│                                                              │
│    // Validate PREMIUM-specific requirements               │
│    validateHeadline(user);  // Min 10, max 255 chars       │
│    validateSummary(user);   // Min 50, max 2000 chars      │
│    validateLocation(user);  // Min 3, max 255 chars        │
│                                                              │
│    // If any validation fails:                             │
│    //   throw new ValidationException(...)                 │
│    //   ↓ Caught by GlobalExceptionHandler                 │
│                                                              │
│    // If all pass, return (void)                           │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘
     │
     ▼ (Back to UserService)
┌─────────────────────────────────────────────────────────────┐
│  LAYER 2: SERVICE (continued)                               │
│                                                              │
│    // STEP 4: Save to database                             │
│    User savedUser = userRepository.save(user);              │
│    //    ↓ Calls Spring Data JPA...                        │
└─────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  PATTERN 3: REPOSITORY (Data Access)                        │
│  📁 UserRepository.java (interface)                         │
│                                                              │
│  public interface UserRepository extends                    │
│      JpaRepository<User, Long> {                            │
│                                                              │
│    // Spring Data JPA auto-implements save()               │
│  }                                                           │
│                                                              │
│  ⚙️ Behind the scenes (Hibernate):                         │
│                                                              │
│    1. Check if user.id is null                             │
│       → Yes, it's a new entity (INSERT)                     │
│                                                              │
│    2. Set audit fields:                                    │
│       - createdAt = now()                                   │
│       - updatedAt = now()                                   │
│       - createdBy = "system" (from SecurityContext)         │
│       - updatedBy = "system"                                │
│                                                              │
│    3. Generate SQL:                                        │
│       INSERT INTO users (                                   │
│           email, password, name, headline, summary,         │
│           location, account_type, is_active,                │
│           email_verified, created_at, updated_at,           │
│           created_by, updated_by                            │
│       ) VALUES (                                            │
│           'jane@example.com',                               │
│           '$2a$10$...',  -- BCrypt hash                     │
│           'Jane Smith',                                     │
│           'Senior Software Engineer',                       │
│           '10 years of experience...',                      │
│           'San Francisco, CA',                              │
│           'PREMIUM',                                        │
│           true,                                             │
│           false,                                            │
│           '2025-12-20 19:30:00',                            │
│           '2025-12-20 19:30:00',                            │
│           'system',                                         │
│           'system'                                          │
│       ) RETURNING id;                                       │
│                                                              │
│    4. Execute query in PostgreSQL                          │
│    5. Get generated ID (e.g., 42)                          │
│    6. Set user.id = 42                                     │
│    7. Return saved user                                    │
└─────────────────────────────────────────────────────────────┘
     │
     ▼ (Back to UserService)
┌─────────────────────────────────────────────────────────────┐
│  LAYER 2: SERVICE (continued)                               │
│                                                              │
│    // STEP 5: Map entity to DTO                            │
│    UserResponse response = userMapper.toResponse(           │
│        savedUser                                            │
│    );                                                        │
│    //    ↓ Calls MapStruct mapper...                       │
└─────────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  PATTERN 4: DTO (Data Transfer)                             │
│  📁 UserMapper.java (MapStruct interface)                   │
│                                                              │
│  @Mapper(componentModel = "spring")                         │
│  public interface UserMapper {                              │
│                                                              │
│    UserResponse toResponse(User user);                      │
│  }                                                           │
│                                                              │
│  ⚙️ MapStruct generates implementation at compile time:    │
│                                                              │
│  public class UserMapperImpl implements UserMapper {        │
│      @Override                                              │
│      public UserResponse toResponse(User user) {            │
│          if (user == null) return null;                     │
│                                                              │
│          UserResponse response = new UserResponse();        │
│          response.setId(user.getId());                      │
│          response.setEmail(user.getEmail());                │
│          response.setName(user.getName());                  │
│          response.setHeadline(user.getHeadline());          │
│          response.setSummary(user.getSummary());            │
│          response.setLocation(user.getLocation());          │
│          response.setAccountType(user.getAccountType());    │
│          response.setIsActive(user.getIsActive());          │
│          response.setCreatedAt(user.getCreatedAt());        │
│          // NOTE: Password is NOT mapped (secure!)         │
│          // NOTE: Audit fields (createdBy, etc.) omitted   │
│                                                              │
│          return response;                                   │
│      }                                                       │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘
     │
     ▼ (Back to UserService, then Controller)
┌─────────────────────────────────────────────────────────────┐
│  LAYER 1: PRESENTATION (Controller - Final Step)            │
│                                                              │
│    // STEP 6: Wrap in ApiResponse                          │
│    ApiResponse<UserResponse> apiResponse =                  │
│        ApiResponse.success(                                 │
│            response,                                        │
│            "Premium user created successfully"              │
│        );                                                    │
│    //    Creates:                                          │
│    //    {                                                 │
│    //      "success": true,                                │
│    //      "message": "Premium user created successfully", │
│    //      "data": { ... UserResponse ... },               │
│    //      "timestamp": "2025-12-20T19:30:00"              │
│    //    }                                                 │
│                                                              │
│    // STEP 7: Return HTTP response                         │
│    return ResponseEntity                                    │
│        .status(HttpStatus.CREATED)  // 201                  │
│        .body(apiResponse);                                  │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘
     │
     ▼
HTTP Response (201 Created)
```

---

## 📤 Final HTTP Response

```http
HTTP/1.1 201 Created
Content-Type: application/json
Transfer-Encoding: chunked
Date: Fri, 20 Dec 2025 19:30:00 GMT

{
  "success": true,
  "message": "Premium user created successfully",
  "data": {
    "id": 42,
    "email": "jane@example.com",
    "name": "Jane Smith",
    "headline": "Senior Software Engineer",
    "summary": "10 years of experience in cloud architecture...",
    "location": "San Francisco, CA",
    "accountType": "PREMIUM",
    "isActive": true,
    "emailVerified": false,
    "createdAt": "2025-12-20T19:30:00"
  },
  "timestamp": "2025-12-20T19:30:00"
}
```

**Notice**:
- ❌ Password is **NOT** included (security!)
- ❌ Audit fields (`createdBy`, `updatedBy`) are **NOT** included (internal)
- ✅ Only safe, client-relevant fields are exposed

---

## 🚨 Error Flow: What if Email Already Exists?

### **Request**
```http
POST http://localhost:8080/api/users/premium
{
  "email": "jane@example.com",  // Already exists!
  "password": "SecurePass456",
  "name": "Jane Smith"
}
```

### **Flow**
```
HTTP Request
     │
     ▼
UserController.createPremiumUser()
     │
     ▼
UserService.createPremiumUser()
     │
     ├─ STEP 1: Check email uniqueness
     │  userRepository.existsByEmail("jane@example.com")
     │    ↓ SQL: SELECT COUNT(*) FROM users WHERE email = ?
     │    ↓ Result: 1 (exists!)
     │
     ├─ Condition: if (exists)
     │    throw new ValidationException(
     │        "Email already exists",
     │        "USER_EMAIL_EXISTS"
     │    );
     │
     ▼
Exception thrown (ValidationException)
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│  EXCEPTION HANDLER                                           │
│  📁 GlobalExceptionHandler.java                             │
│                                                              │
│  @ExceptionHandler(ValidationException.class)               │
│  public ResponseEntity<ErrorResponse> handle(               │
│      ValidationException ex                                 │
│  ) {                                                         │
│                                                              │
│    // Log warning                                           │
│    log.warn("ValidationException: {}", ex.getMessage());    │
│                                                              │
│    // Create error response                                │
│    ErrorResponse errorResponse = ErrorResponse.builder()    │
│        .errorCode("USER_EMAIL_EXISTS")                      │
│        .message("Email already exists")                     │
│        .timestamp(LocalDateTime.now())                      │
│        .status(400)                                         │
│        .path("/api/users/premium")                          │
│        .build();                                            │
│                                                              │
│    // Return 400 Bad Request                               │
│    return ResponseEntity                                    │
│        .status(HttpStatus.BAD_REQUEST)                      │
│        .body(errorResponse);                                │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘
     │
     ▼
HTTP Response (400 Bad Request)
```

### **Error Response**
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "errorCode": "USER_EMAIL_EXISTS",
  "message": "Email already exists",
  "timestamp": "2025-12-20T19:30:00",
  "status": 400,
  "path": "/api/users/premium"
}
```

---

## 🧩 Component Interactions Summary

### **Controller ↔ Service**
- **Controller**: HTTP layer, input validation, response formatting
- **Service**: Business logic, orchestration, transaction management
- **Communication**: DTOs (CreateUserRequest → UserResponse)

### **Service ↔ Factory**
- **Service**: Requests user creation
- **Factory**: Handles object creation, password hashing, default values
- **Communication**: CreateUserRequest → User entity

### **Service ↔ Strategy**
- **Service**: Requests profile validation
- **Strategy**: Validates based on account type (BASIC vs PREMIUM)
- **Communication**: User entity → void (or exception)

### **Service ↔ Repository**
- **Service**: Requests data persistence
- **Repository**: Executes SQL queries
- **Communication**: User entity → User entity (with ID)

### **Service ↔ Mapper**
- **Service**: Requests entity-to-DTO conversion
- **Mapper**: Maps fields
- **Communication**: User entity → UserResponse DTO

---

## 🔄 Transaction Management

### **@Transactional Annotation**
```java
@Service
@Transactional(readOnly = true)  // Default for all methods
public class UserService {

    @Transactional  // Override: this method writes
    public UserResponse createPremiumUser(CreateUserRequest request) {
        // All database operations in this method are in ONE transaction
        
        // Operation 1: Check email
        userRepository.existsByEmail(email);
        
        // Operation 2: Save user
        userRepository.save(user);
        
        // If ANY operation fails, ENTIRE transaction rolls back
        // Database remains in consistent state
    }
}
```

### **What Happens Behind the Scenes**
```
1. @Transactional method called
   ↓
2. Spring creates database connection
   ↓
3. BEGIN TRANSACTION
   ↓
4. Execute: SELECT COUNT(*) FROM users WHERE email = ?
   ↓
5. Execute: INSERT INTO users (...) VALUES (...)
   ↓
6. No exceptions?
   ↓ Yes
7. COMMIT TRANSACTION
   ↓
8. Close connection
   ↓
9. Return result

❌ If exception occurs at any step:
   - ROLLBACK TRANSACTION
   - Database unchanged
   - Exception propagated to controller
   - GlobalExceptionHandler catches it
```

---

## 🎭 Design Patterns in Action

### **Why Factory Pattern?**
**Without Factory**:
```java
// Service layer has to know:
User user = new User();
user.setEmail(request.getEmail());
user.setPassword(passwordEncoder.encode(request.getPassword())); // Oops, forgot to hash!
user.setAccountType(AccountType.BASIC);
user.setIsActive(true);
user.setEmailVerified(false);
// ... 10 more lines of boilerplate ...
```

**With Factory**:
```java
// Service layer is clean:
User user = userFactory.createUser(request); // One line! ✅
```

**Benefits**:
- ✅ Centralized object creation logic
- ✅ Password hashing never forgotten
- ✅ Default values always set
- ✅ Easy to test in isolation
- ✅ Single source of truth

---

### **Why Strategy Pattern?**
**Without Strategy**:
```java
// Service layer has if-else hell:
public void validateProfile(User user) {
    if (user.getAccountType() == AccountType.BASIC) {
        // Validate BASIC requirements
        if (user.getName() == null) throw new ValidationException(...);
        // ... 20 lines ...
    } else if (user.getAccountType() == AccountType.PREMIUM) {
        // Validate PREMIUM requirements
        if (user.getName() == null) throw new ValidationException(...);
        if (user.getHeadline() == null) throw new ValidationException(...);
        // ... 50 lines ...
    } else if (user.getAccountType() == AccountType.ENTERPRISE) {
        // ... another 100 lines ...
    }
    // Service layer becomes HUGE!
}
```

**With Strategy**:
```java
// Service layer is clean:
ProfileValidationStrategy strategy = strategyFactory.getStrategy(user.getAccountType());
strategy.validate(user); // Polymorphism! ✅

// Adding new account type?
// 1. Create new strategy class
// 2. No changes to service layer!
```

**Benefits**:
- ✅ Open/Closed Principle (open for extension, closed for modification)
- ✅ Each strategy is a separate class (Single Responsibility)
- ✅ Easy to add new account types (ENTERPRISE, TRIAL, etc.)
- ✅ Easy to test each strategy in isolation

---

### **Why Repository Pattern?**
**Without Repository**:
```java
// Service layer has raw SQL:
EntityManager em = entityManagerFactory.createEntityManager();
EntityTransaction tx = em.getTransaction();
tx.begin();
Query query = em.createQuery("SELECT u FROM User u WHERE u.email = :email");
query.setParameter("email", email);
User user = (User) query.getSingleResult();
tx.commit();
em.close();
// ... error handling ...
```

**With Repository**:
```java
// Service layer is clean:
User user = userRepository.findByEmail(email).orElseThrow(); // One line! ✅
```

**Benefits**:
- ✅ Abstract database details
- ✅ Easy to switch databases (PostgreSQL → MySQL)
- ✅ Easy to test (mock repository)
- ✅ Automatic transaction management

---

### **Why DTO Pattern?**
**Without DTO**:
```java
// Return entity directly:
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userService.getUserById(id);
}

// Response includes EVERYTHING:
{
  "id": 1,
  "email": "jane@example.com",
  "password": "$2a$10$...",  // ❌ SECURITY ISSUE!
  "createdBy": "admin",       // ❌ Internal detail
  "updatedAt": "..."          // ❌ Too much info
}
```

**With DTO**:
```java
// Return DTO:
@GetMapping("/{id}")
public UserResponse getUser(@PathVariable Long id) {
    return userService.getUserById(id);  // Returns UserResponse DTO
}

// Response includes ONLY safe fields:
{
  "id": 1,
  "email": "jane@example.com",
  "name": "Jane Smith",
  "accountType": "PREMIUM"
  // ✅ No password, no audit fields
}
```

**Benefits**:
- ✅ Security (no sensitive data exposure)
- ✅ Clean API contract
- ✅ Versioning (UserResponseV1, UserResponseV2)
- ✅ Decoupling (entity changes don't affect API)

---

## 🎓 Key Takeaways

1. **Layered Architecture** separates concerns:
   - **Controller**: HTTP, validation, response formatting
   - **Service**: Business logic, orchestration
   - **Repository**: Data access
   - **Patterns**: Reusable solutions (Factory, Strategy)

2. **Each layer has a clear responsibility**:
   - Don't put business logic in controllers
   - Don't put SQL in service layer
   - Don't expose entities directly to clients

3. **Design patterns solve specific problems**:
   - **Factory**: Centralize object creation
   - **Strategy**: Behavior varies by type
   - **Repository**: Abstract data access
   - **DTO**: Clean API contracts

4. **Transaction management is automatic**:
   - `@Transactional` ensures atomicity
   - All-or-nothing database operations

5. **Exception handling is centralized**:
   - `@ControllerAdvice` catches all exceptions
   - Consistent error responses

---

*This document shows the complete journey of a single HTTP request through the entire system, demonstrating how each component interacts and why design patterns are essential for maintainability.* 🚀

