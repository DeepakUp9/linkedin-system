# 🚀 Quick Start Guide

Your one-stop reference for all commands to build, run, and test the LinkedIn User Service.

---

## 📋 Prerequisites Checklist

```bash
# Check Java version (requires Java 17+)
java -version
# Expected: openjdk version "17.x.x" or higher

# Check Maven version (requires Maven 3.8+)
mvn -version
# Expected: Apache Maven 3.8.x or higher

# Check PostgreSQL (requires PostgreSQL 15+)
psql --version
# Expected: psql (PostgreSQL) 15.x or higher

# Check if PostgreSQL is running
psql -U postgres -c "SELECT version();"
# Should connect successfully
```

---

## 🗄️ Database Setup

### 1. Create Database
```bash
# Option 1: Using psql
psql -U postgres
CREATE DATABASE linkedin_users;
\q

# Option 2: Using createdb command
createdb -U postgres linkedin_users

# Verify database exists
psql -U postgres -l | grep linkedin_users
```

### 2. Create User (Optional)
```bash
psql -U postgres
CREATE USER linkedin_app WITH PASSWORD 'linkedin_pass';
GRANT ALL PRIVILEGES ON DATABASE linkedin_users TO linkedin_app;
\q
```

### 3. Verify Connection
```bash
psql -U postgres -d linkedin_users -c "SELECT 1;"
# Expected: 1 row returned
```

---

## 🏗️ Build Project

### Build Everything
```bash
# Navigate to project root
cd /Users/deepakkumar/linkedin-system

# Clean and build all modules
mvn clean install

# Expected output:
# [INFO] linkedin-system ................................ SUCCESS
# [INFO] common-lib ..................................... SUCCESS
# [INFO] user-service ................................... SUCCESS
```

### Build Specific Module
```bash
# Build only common-lib
mvn clean install -pl common-lib

# Build only user-service (includes common-lib as dependency)
mvn clean install -pl user-service -am
```

### Skip Tests
```bash
# Build without running tests (faster)
mvn clean install -DskipTests
```

### Compile Only (No Package)
```bash
mvn clean compile
```

---

## 🚀 Run Application

### Method 1: Maven Spring Boot Plugin (Development)
```bash
cd user-service
mvn spring-boot:run

# Application starts on http://localhost:8080
```

### Method 2: Java JAR (Production-like)
```bash
# First, build the JAR
cd /Users/deepakkumar/linkedin-system
mvn clean package -pl user-service -am -DskipTests

# Then run the JAR
cd user-service
java -jar target/user-service-1.0.0-SNAPSHOT.jar
```

### Method 3: Run in Background
```bash
cd user-service
nohup mvn spring-boot:run > application.log 2>&1 &

# Check logs
tail -f application.log

# Stop background process
ps aux | grep spring-boot
kill <PID>
```

---

## ✅ Verify Application

### Health Check
```bash
# Check if application is running
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}
```

### Test Basic Endpoint
```bash
# Get all users (should return empty or sample data)
curl http://localhost:8080/api/users

# Expected response:
# {
#   "success": true,
#   "message": "Users retrieved successfully",
#   "data": { ... },
#   "timestamp": "..."
# }
```

---

## 📝 API Testing Commands

### Create BASIC User
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "SecurePass123",
    "name": "John Doe"
  }'

# Expected: 201 Created
```

### Create PREMIUM User
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

# Expected: 201 Created
```

### Get User by ID
```bash
curl http://localhost:8080/api/users/1

# Pretty print with jq (if installed)
curl http://localhost:8080/api/users/1 | jq '.'
```

### Get User by Email
```bash
curl http://localhost:8080/api/users/email/john@example.com
```

### Get All Users (Paginated)
```bash
# First page (default: 20 items)
curl http://localhost:8080/api/users

# Second page with 10 items per page
curl "http://localhost:8080/api/users?page=1&size=10"

# Get 50 items
curl "http://localhost:8080/api/users?size=50"
```

### Get Active Users
```bash
curl http://localhost:8080/api/users/active
```

### Get Users by Type
```bash
# Get all PREMIUM users
curl http://localhost:8080/api/users/type/PREMIUM

# Get BASIC users
curl http://localhost:8080/api/users/type/BASIC
```

### Search Users
```bash
# Search for "engineer"
curl "http://localhost:8080/api/users/search?q=engineer"

# Search for "engineer" in PREMIUM accounts only
curl "http://localhost:8080/api/users/search?q=engineer&type=PREMIUM"

# Search with pagination
curl "http://localhost:8080/api/users/search?q=developer&page=0&size=10"
```

### Get Recent Users
```bash
# Users who joined in last 7 days (default)
curl http://localhost:8080/api/users/recent

# Users who joined in last 30 days
curl "http://localhost:8080/api/users/recent?days=30"

# Users who joined today
curl "http://localhost:8080/api/users/recent?days=1"
```

### Update User
```bash
# Update single field
curl -X PATCH http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "John Smith"}'

# Update multiple fields
curl -X PATCH http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{
    "headline": "Senior Full Stack Developer",
    "location": "New York, NY",
    "currentCompany": "Tech Corp"
  }'
```

### Delete User (Soft Delete)
```bash
curl -X DELETE http://localhost:8080/api/users/1

# Expected: 204 No Content
```

### Upgrade to PREMIUM
```bash
curl -X POST http://localhost:8080/api/users/1/upgrade

# Expected: 200 OK
```

---

## 🧪 Testing Workflows

### Complete User Lifecycle Test
```bash
#!/bin/bash

echo "1. Creating BASIC user..."
USER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Test123",
    "name": "Test User"
  }')

USER_ID=$(echo $USER_RESPONSE | jq -r '.data.id')
echo "Created user with ID: $USER_ID"

echo "2. Getting user by ID..."
curl -s http://localhost:8080/api/users/$USER_ID | jq '.'

echo "3. Updating user profile..."
curl -s -X PATCH http://localhost:8080/api/users/$USER_ID \
  -H "Content-Type: application/json" \
  -d '{
    "headline": "Full Stack Developer",
    "summary": "Experienced developer with 5 years in web development and cloud technologies.",
    "location": "Seattle, WA"
  }' | jq '.'

echo "4. Upgrading to PREMIUM..."
curl -s -X POST http://localhost:8080/api/users/$USER_ID/upgrade | jq '.'

echo "5. Verifying upgrade..."
curl -s http://localhost:8080/api/users/$USER_ID | jq '.data.accountType'

echo "6. Soft deleting user..."
curl -s -X DELETE http://localhost:8080/api/users/$USER_ID

echo "Done!"
```

---

## 🗄️ Database Commands

### Connect to Database
```bash
psql -U postgres -d linkedin_users
```

### Useful SQL Queries
```sql
-- See all users
SELECT id, email, name, account_type, is_active, created_at 
FROM users 
ORDER BY created_at DESC;

-- Count users by account type
SELECT account_type, COUNT(*) 
FROM users 
GROUP BY account_type;

-- Find active PREMIUM users
SELECT id, email, name, headline 
FROM users 
WHERE account_type = 'PREMIUM' AND is_active = true;

-- Search users by name (case-insensitive)
SELECT id, email, name, headline 
FROM users 
WHERE LOWER(name) LIKE '%john%';

-- Get recent users (last 7 days)
SELECT id, email, name, created_at 
FROM users 
WHERE created_at > NOW() - INTERVAL '7 days' 
ORDER BY created_at DESC;

-- Check Flyway migration history
SELECT * FROM flyway_schema_history;

-- See all indexes
SELECT tablename, indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'users';
```

### Reset Database (Caution!)
```sql
-- Drop all data
TRUNCATE TABLE users RESTART IDENTITY CASCADE;

-- Or drop and recreate database
DROP DATABASE linkedin_users;
CREATE DATABASE linkedin_users;
```

---

## 📊 Monitoring & Logs

### View Application Logs
```bash
# If running with Maven
mvn spring-boot:run

# If running as JAR
java -jar target/user-service-1.0.0-SNAPSHOT.jar

# If running in background
tail -f application.log

# Follow logs with grep
tail -f application.log | grep ERROR
```

### Check Application Metrics
```bash
# Health check
curl http://localhost:8080/actuator/health

# Application info
curl http://localhost:8080/actuator/info

# Available endpoints
curl http://localhost:8080/actuator

# Database connection details
curl http://localhost:8080/actuator/health/db
```

---

## 🐛 Troubleshooting

### Application Won't Start

#### Issue: Port 8080 already in use
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process
kill -9 <PID>

# Or use a different port
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

#### Issue: Database connection failed
```bash
# Check if PostgreSQL is running
pg_isready -U postgres

# Check if database exists
psql -U postgres -l | grep linkedin_users

# Create database if missing
createdb -U postgres linkedin_users

# Verify connection
psql -U postgres -d linkedin_users -c "SELECT 1;"
```

#### Issue: Flyway migration failed
```bash
# Check migration history
psql -U postgres -d linkedin_users -c "SELECT * FROM flyway_schema_history;"

# If needed, repair Flyway
mvn flyway:repair

# Or clean and recreate
psql -U postgres
DROP DATABASE linkedin_users;
CREATE DATABASE linkedin_users;
\q
```

### Build Fails

#### Issue: Maven compilation errors
```bash
# Clean Maven cache
mvn clean

# Rebuild with fresh dependencies
mvn clean install -U

# Skip tests
mvn clean install -DskipTests
```

#### Issue: Lombok not working
```bash
# Verify Lombok is in classpath
mvn dependency:tree | grep lombok

# Rebuild with clean compile
mvn clean compile
```

### API Errors

#### Issue: 404 Not Found
```bash
# Verify application is running
curl http://localhost:8080/actuator/health

# Check correct endpoint
curl http://localhost:8080/api/users
```

#### Issue: 400 Validation Error
```bash
# Check request body format
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"email":"invalid","password":"","name":""}' \
  -v

# Read error response for details
```

---

## 📚 Documentation Links

- **Main README**: `README.md`
- **Architecture Guide**: `ARCHITECTURE.md`
- **API Reference**: `API_REFERENCE.md`
- **Learning Guide**: `LEARNING_GUIDE.md`
- **Visual Overview**: `VISUAL_OVERVIEW.md`
- **Completion Summary**: `COMPLETION_SUMMARY.md`

---

## 🔗 OpenAPI/Swagger UI

Once the application is running, access interactive API documentation:

```
http://localhost:8080/swagger-ui/index.html
```

Features:
- View all endpoints
- Test endpoints directly from browser
- See request/response schemas
- Generate sample requests

---

## 🎯 Common Development Commands

```bash
# Quick start (from project root)
cd /Users/deepakkumar/linkedin-system && \
  mvn clean install -DskipTests && \
  cd user-service && \
  mvn spring-boot:run

# Full rebuild and run
mvn clean install && cd user-service && mvn spring-boot:run

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Package for deployment
mvn clean package -DskipTests

# Check for dependency updates
mvn versions:display-dependency-updates

# Format code (if formatter plugin configured)
mvn formatter:format
```

---

## 🚀 Next Steps

After verifying everything works:

1. **Test all 12 endpoints** using the curl commands above
2. **Explore the OpenAPI UI** at http://localhost:8080/swagger-ui/index.html
3. **Read the Architecture Guide** to understand the complete flow
4. **Implement Authentication** as the next feature set
5. **Add more microservices** (Posts, Connections, Messaging)

---

*Your complete command reference for building and running the LinkedIn User Service!* 🎉

