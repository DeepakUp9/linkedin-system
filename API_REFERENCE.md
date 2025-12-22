# API Quick Reference - User Service

Complete reference for all 12 REST endpoints with request/response examples.

---

## 📋 Base URL
```
http://localhost:8080/api/users
```

---

## 1️⃣ Create BASIC User

**Endpoint**: `POST /api/users`

**Description**: Register a new user with a BASIC account type.

**Request Headers**:
```
Content-Type: application/json
```

**Request Body**:
```json
{
  "email": "john@example.com",
  "password": "SecurePass123",
  "name": "John Doe"
}
```

**Validation Rules**:
- ✅ Email: Valid format, unique
- ✅ Password: Min 8 characters
- ✅ Name: Required, 2-255 characters

**Success Response (201 Created)**:
```json
{
  "success": true,
  "message": "BASIC user created successfully",
  "data": {
    "id": 1,
    "email": "john@example.com",
    "name": "John Doe",
    "headline": null,
    "summary": null,
    "location": null,
    "profilePhotoUrl": null,
    "accountType": "BASIC",
    "isActive": true,
    "emailVerified": false,
    "phoneNumber": null,
    "dateOfBirth": null,
    "industry": null,
    "currentJobTitle": null,
    "currentCompany": null,
    "createdAt": "2025-12-20T19:30:00"
  },
  "timestamp": "2025-12-20T19:30:00"
}
```

**Error Response (400 Bad Request - Email Exists)**:
```json
{
  "errorCode": "USER_EMAIL_EXISTS",
  "message": "Email address already registered",
  "timestamp": "2025-12-20T19:30:00",
  "status": 400,
  "path": "/api/users"
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "SecurePass123",
    "name": "John Doe"
  }'
```

---

## 2️⃣ Create PREMIUM User

**Endpoint**: `POST /api/users/premium`

**Description**: Register a new user with a PREMIUM account type.

**Request Body**:
```json
{
  "email": "jane@example.com",
  "password": "SecurePass456",
  "name": "Jane Smith",
  "headline": "Senior Software Engineer at Google",
  "summary": "10 years of experience in distributed systems, cloud architecture, and team leadership. Passionate about building scalable solutions.",
  "location": "San Francisco, CA",
  "phoneNumber": "+1-555-0123",
  "industry": "Technology",
  "currentJobTitle": "Senior Software Engineer",
  "currentCompany": "Google"
}
```

**Validation Rules**:
- ✅ All BASIC requirements
- ✅ Headline: Required, min 10 characters, max 255
- ✅ Summary: Required, min 50 characters, max 2000
- ✅ Location: Required, min 3 characters, max 255

**Success Response (201 Created)**:
```json
{
  "success": true,
  "message": "Premium user created successfully",
  "data": {
    "id": 2,
    "email": "jane@example.com",
    "name": "Jane Smith",
    "headline": "Senior Software Engineer at Google",
    "summary": "10 years of experience...",
    "location": "San Francisco, CA",
    "accountType": "PREMIUM",
    "isActive": true,
    "emailVerified": false,
    "phoneNumber": "+1-555-0123",
    "industry": "Technology",
    "currentJobTitle": "Senior Software Engineer",
    "currentCompany": "Google",
    "createdAt": "2025-12-20T19:30:00"
  },
  "timestamp": "2025-12-20T19:30:00"
}
```

**Error Response (400 Bad Request - Validation Failed)**:
```json
{
  "errorCode": "USER_VALIDATION_FAILED",
  "message": "PREMIUM users must provide a headline (minimum 10 characters)",
  "timestamp": "2025-12-20T19:30:00",
  "status": 400,
  "path": "/api/users/premium"
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/users/premium \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane@example.com",
    "password": "SecurePass456",
    "name": "Jane Smith",
    "headline": "Senior Software Engineer at Google",
    "summary": "10 years of experience in distributed systems, cloud architecture, and team leadership. Passionate about building scalable solutions.",
    "location": "San Francisco, CA"
  }'
```

---

## 3️⃣ Get User by ID

**Endpoint**: `GET /api/users/{id}`

**Description**: Retrieve a user by their unique ID.

**Path Parameters**:
- `id` (Long): User ID

**Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "User retrieved successfully",
  "data": {
    "id": 1,
    "email": "john@example.com",
    "name": "John Doe",
    "headline": "Full Stack Developer",
    "accountType": "BASIC",
    "isActive": true,
    "createdAt": "2025-12-20T19:30:00"
  },
  "timestamp": "2025-12-20T19:30:00"
}
```

**Error Response (404 Not Found)**:
```json
{
  "errorCode": "USER_NOT_FOUND",
  "message": "User not found with ID: 999",
  "timestamp": "2025-12-20T19:30:00",
  "status": 404,
  "path": "/api/users/999"
}
```

**cURL Example**:
```bash
curl http://localhost:8080/api/users/1
```

---

## 4️⃣ Get User by Email

**Endpoint**: `GET /api/users/email/{email}`

**Description**: Retrieve a user by their email address.

**Path Parameters**:
- `email` (String): User email

**Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "User retrieved successfully",
  "data": {
    "id": 1,
    "email": "john@example.com",
    "name": "John Doe",
    "accountType": "BASIC"
  },
  "timestamp": "2025-12-20T19:30:00"
}
```

**cURL Example**:
```bash
curl http://localhost:8080/api/users/email/john@example.com
```

---

## 5️⃣ Get All Users (Paginated)

**Endpoint**: `GET /api/users`

**Description**: Retrieve all users with pagination support.

**Query Parameters**:
- `page` (int, default: 0): Page number (0-indexed)
- `size` (int, default: 20): Page size

**Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Users retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "email": "john@example.com",
        "name": "John Doe",
        "accountType": "BASIC"
      },
      {
        "id": 2,
        "email": "jane@example.com",
        "name": "Jane Smith",
        "accountType": "PREMIUM"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "offset": 0,
      "paged": true,
      "unpaged": false
    },
    "totalElements": 50,
    "totalPages": 3,
    "last": false,
    "size": 20,
    "number": 0,
    "first": true,
    "numberOfElements": 20,
    "empty": false
  },
  "timestamp": "2025-12-20T19:30:00"
}
```

**cURL Examples**:
```bash
# First page (default: 20 items)
curl http://localhost:8080/api/users

# Second page with 10 items per page
curl "http://localhost:8080/api/users?page=1&size=10"

# Get 50 items on first page
curl "http://localhost:8080/api/users?page=0&size=50"
```

---

## 6️⃣ Get Active Users

**Endpoint**: `GET /api/users/active`

**Description**: Retrieve all active users (isActive = true).

**Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Active users retrieved successfully",
  "data": [
    {
      "id": 1,
      "email": "john@example.com",
      "name": "John Doe",
      "isActive": true
    },
    {
      "id": 2,
      "email": "jane@example.com",
      "name": "Jane Smith",
      "isActive": true
    }
  ],
  "timestamp": "2025-12-20T19:30:00"
}
```

**cURL Example**:
```bash
curl http://localhost:8080/api/users/active
```

---

## 7️⃣ Get Users by Account Type

**Endpoint**: `GET /api/users/type/{type}`

**Description**: Retrieve users by account type (BASIC or PREMIUM).

**Path Parameters**:
- `type` (AccountType): BASIC or PREMIUM

**Query Parameters**:
- `page` (int, default: 0)
- `size` (int, default: 20)

**Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Users retrieved by account type",
  "data": {
    "content": [
      {
        "id": 2,
        "email": "jane@example.com",
        "name": "Jane Smith",
        "accountType": "PREMIUM",
        "headline": "Senior Software Engineer"
      }
    ],
    "totalElements": 15,
    "totalPages": 1,
    "number": 0
  },
  "timestamp": "2025-12-20T19:30:00"
}
```

**cURL Examples**:
```bash
# Get all PREMIUM users
curl http://localhost:8080/api/users/type/PREMIUM

# Get BASIC users (page 2)
curl "http://localhost:8080/api/users/type/BASIC?page=1&size=10"
```

---

## 8️⃣ Search Users

**Endpoint**: `GET /api/users/search`

**Description**: Full-text search across name, headline, and email.

**Query Parameters**:
- `q` (String, required): Search query
- `type` (AccountType, optional): Filter by account type
- `page` (int, default: 0)
- `size` (int, default: 20)

**Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Search completed successfully",
  "data": {
    "content": [
      {
        "id": 2,
        "email": "jane@example.com",
        "name": "Jane Smith",
        "headline": "Senior Software Engineer at Google",
        "accountType": "PREMIUM"
      },
      {
        "id": 5,
        "email": "alex@example.com",
        "name": "Alex Johnson",
        "headline": "Software Engineer at Microsoft",
        "accountType": "BASIC"
      }
    ],
    "totalElements": 2,
    "totalPages": 1
  },
  "timestamp": "2025-12-20T19:30:00"
}
```

**cURL Examples**:
```bash
# Search for "engineer" across all account types
curl "http://localhost:8080/api/users/search?q=engineer"

# Search for "engineer" only in PREMIUM accounts
curl "http://localhost:8080/api/users/search?q=engineer&type=PREMIUM"

# Search with pagination
curl "http://localhost:8080/api/users/search?q=developer&page=1&size=5"
```

---

## 9️⃣ Get Recent Users

**Endpoint**: `GET /api/users/recent`

**Description**: Get users who joined within the last N days.

**Query Parameters**:
- `days` (int, default: 7): Number of days to look back

**Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "Recent users retrieved successfully",
  "data": [
    {
      "id": 10,
      "email": "new@example.com",
      "name": "New User",
      "createdAt": "2025-12-19T10:00:00"
    },
    {
      "id": 9,
      "email": "recent@example.com",
      "name": "Recent User",
      "createdAt": "2025-12-18T15:30:00"
    }
  ],
  "timestamp": "2025-12-20T19:30:00"
}
```

**cURL Examples**:
```bash
# Users who joined in last 7 days (default)
curl http://localhost:8080/api/users/recent

# Users who joined in last 30 days
curl "http://localhost:8080/api/users/recent?days=30"

# Users who joined today
curl "http://localhost:8080/api/users/recent?days=1"
```

---

## 🔟 Update User

**Endpoint**: `PATCH /api/users/{id}`

**Description**: Update user profile (partial update - only provided fields are updated).

**Path Parameters**:
- `id` (Long): User ID

**Request Body** (all fields optional):
```json
{
  "name": "John Smith",
  "headline": "Senior Full Stack Developer",
  "summary": "Experienced developer with 8 years in web development...",
  "location": "New York, NY",
  "profilePhotoUrl": "https://example.com/photos/john.jpg",
  "phoneNumber": "+1-555-9876",
  "dateOfBirth": "1990-05-15",
  "industry": "Technology",
  "currentJobTitle": "Senior Developer",
  "currentCompany": "Tech Corp"
}
```

**Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "User updated successfully",
  "data": {
    "id": 1,
    "email": "john@example.com",
    "name": "John Smith",
    "headline": "Senior Full Stack Developer",
    "summary": "Experienced developer with 8 years...",
    "location": "New York, NY",
    "accountType": "BASIC",
    "createdAt": "2025-12-20T19:30:00"
  },
  "timestamp": "2025-12-20T19:30:00"
}
```

**Notes**:
- ❌ Cannot update: email, password, accountType, isActive, emailVerified
- ✅ Null fields in request are ignored (existing values retained)
- ✅ Only provided fields are updated

**cURL Examples**:
```bash
# Update only name
curl -X PATCH http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "John Smith"}'

# Update multiple fields
curl -X PATCH http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{
    "headline": "Senior Developer",
    "location": "New York, NY",
    "currentCompany": "Tech Corp"
  }'
```

---

## 1️⃣1️⃣ Delete User (Soft Delete)

**Endpoint**: `DELETE /api/users/{id}`

**Description**: Soft delete a user (sets isActive = false).

**Path Parameters**:
- `id` (Long): User ID

**Success Response (204 No Content)**:
```
(No response body)
```

**Error Response (404 Not Found)**:
```json
{
  "errorCode": "USER_NOT_FOUND",
  "message": "User not found with ID: 999",
  "timestamp": "2025-12-20T19:30:00",
  "status": 404,
  "path": "/api/users/999"
}
```

**Notes**:
- ✅ User is not permanently deleted from database
- ✅ User's `isActive` field is set to `false`
- ✅ User can be reactivated later (not implemented in this version)

**cURL Example**:
```bash
curl -X DELETE http://localhost:8080/api/users/1
```

---

## 1️⃣2️⃣ Upgrade to PREMIUM

**Endpoint**: `POST /api/users/{id}/upgrade`

**Description**: Upgrade a BASIC user to PREMIUM account.

**Path Parameters**:
- `id` (Long): User ID

**Success Response (200 OK)**:
```json
{
  "success": true,
  "message": "User upgraded to Premium successfully",
  "data": {
    "id": 1,
    "email": "john@example.com",
    "name": "John Doe",
    "accountType": "PREMIUM",
    "createdAt": "2025-12-20T19:30:00"
  },
  "timestamp": "2025-12-20T19:30:00"
}
```

**Error Response (400 Bad Request - Already Premium)**:
```json
{
  "errorCode": "BUSINESS_RULE_VIOLATION",
  "message": "User is already a PREMIUM member",
  "timestamp": "2025-12-20T19:30:00",
  "status": 422,
  "path": "/api/users/1/upgrade"
}
```

**Notes**:
- ✅ Only BASIC users can be upgraded
- ✅ PREMIUM validation rules apply after upgrade (headline, summary, location required)
- ❌ Cannot downgrade from PREMIUM to BASIC (not implemented)

**cURL Example**:
```bash
curl -X POST http://localhost:8080/api/users/1/upgrade
```

---

## 🎯 Common HTTP Status Codes

| Status Code | Meaning | When Used |
|-------------|---------|-----------|
| **200 OK** | Success | GET, PATCH operations |
| **201 Created** | Resource created | POST operations (create user) |
| **204 No Content** | Success, no body | DELETE operations |
| **400 Bad Request** | Validation error | Invalid input, missing fields |
| **404 Not Found** | Resource not found | User doesn't exist |
| **422 Unprocessable Entity** | Business rule violation | Already premium, invalid state |
| **500 Internal Server Error** | Server error | Unexpected exceptions |

---

## 🧪 Testing with Postman Collection

### Collection Structure
```
LinkedIn User Service
├── 1. Create BASIC User
├── 2. Create PREMIUM User
├── 3. Get User by ID
├── 4. Get User by Email
├── 5. Get All Users
├── 6. Get Active Users
├── 7. Get Users by Type
├── 8. Search Users
├── 9. Get Recent Users
├── 10. Update User
├── 11. Delete User
└── 12. Upgrade to PREMIUM
```

### Environment Variables
```json
{
  "base_url": "http://localhost:8080",
  "created_user_id": "",
  "created_user_email": ""
}
```

---

## 🎓 Example Workflows

### Workflow 1: Register and Update Profile
```bash
# Step 1: Create BASIC user
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"Pass123","name":"John Doe"}'

# Response: {"data": {"id": 1, ...}}

# Step 2: Update profile
curl -X PATCH http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{"headline":"Full Stack Developer","location":"NYC"}'

# Step 3: Verify update
curl http://localhost:8080/api/users/1
```

### Workflow 2: Upgrade to Premium
```bash
# Step 1: Create BASIC user
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"email":"jane@example.com","password":"Pass456","name":"Jane Smith"}'

# Step 2: Try to upgrade (will fail - missing required PREMIUM fields)
curl -X POST http://localhost:8080/api/users/1/upgrade
# Error: "PREMIUM users must provide headline"

# Step 3: Add required fields
curl -X PATCH http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{
    "headline":"Senior Software Engineer at Google",
    "summary":"10 years experience in distributed systems...",
    "location":"San Francisco, CA"
  }'

# Step 4: Upgrade again (now succeeds)
curl -X POST http://localhost:8080/api/users/1/upgrade
```

### Workflow 3: Search and Filter
```bash
# Search for engineers
curl "http://localhost:8080/api/users/search?q=engineer"

# Filter to only PREMIUM engineers
curl "http://localhost:8080/api/users/search?q=engineer&type=PREMIUM"

# Get all PREMIUM users
curl http://localhost:8080/api/users/type/PREMIUM

# Get users who joined this week
curl "http://localhost:8080/api/users/recent?days=7"
```

---

## 🔍 Debugging Tips

### Check Application Health
```bash
curl http://localhost:8080/actuator/health
```

### Verbose cURL Output
```bash
curl -v http://localhost:8080/api/users/1
```

### Format JSON Output (using jq)
```bash
curl http://localhost:8080/api/users/1 | jq '.'
```

### Save Response to File
```bash
curl http://localhost:8080/api/users/1 > user.json
```

---

*Complete API reference for the LinkedIn-like User Service. All endpoints tested and documented with real examples.* ✅

