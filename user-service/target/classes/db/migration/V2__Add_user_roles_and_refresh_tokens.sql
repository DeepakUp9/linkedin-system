-- =============================================================================
-- Migration: V2__Add_user_roles_and_refresh_tokens.sql
-- Description: Add role-based access control and refresh token functionality
-- Author: System
-- Date: 2025-12-20
-- =============================================================================

-- =============================================================================
-- 1. CREATE USER_ROLES TABLE
-- =============================================================================
-- This table stores the many-to-many relationship between users and roles.
-- A user can have multiple roles (USER, ADMIN).
-- This is an ElementCollection in JPA, mapped as a join table.

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,
    
    -- Primary key: combination of user_id and role (prevents duplicates)
    PRIMARY KEY (user_id, role),
    
    -- Foreign key: references users table
    -- ON DELETE CASCADE: When user is deleted, their roles are deleted
    CONSTRAINT fk_user_roles_user_id FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE
);

-- Index for efficient role lookups
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);

-- Index for finding all users with a specific role
CREATE INDEX idx_user_roles_role ON user_roles(role);

COMMENT ON TABLE user_roles IS 'Join table for user roles (RBAC - Role-Based Access Control)';
COMMENT ON COLUMN user_roles.user_id IS 'Reference to users table';
COMMENT ON COLUMN user_roles.role IS 'Role name (USER, ADMIN)';

-- =============================================================================
-- 2. CREATE REFRESH_TOKENS TABLE
-- =============================================================================
-- This table stores refresh tokens for JWT authentication.
-- Refresh tokens are long-lived (7 days) and can be used to obtain new access tokens.

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key: references users table
    -- ON DELETE CASCADE: When user is deleted, their refresh tokens are deleted
    CONSTRAINT fk_refresh_tokens_user_id FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE
);

-- Index for fast token lookups (most common query)
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);

-- Index for finding all refresh tokens for a user
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- Index for finding expired tokens (for cleanup job)
CREATE INDEX idx_refresh_tokens_expiry_date ON refresh_tokens(expiry_date);

COMMENT ON TABLE refresh_tokens IS 'Refresh tokens for JWT authentication';
COMMENT ON COLUMN refresh_tokens.id IS 'Primary key';
COMMENT ON COLUMN refresh_tokens.token IS 'UUID token value (unique)';
COMMENT ON COLUMN refresh_tokens.user_id IS 'User who owns this token';
COMMENT ON COLUMN refresh_tokens.expiry_date IS 'When this token expires (typically 7 days)';
COMMENT ON COLUMN refresh_tokens.created_at IS 'When this token was created';

-- =============================================================================
-- 3. INSERT DEFAULT ROLES FOR EXISTING USERS
-- =============================================================================
-- All existing users should have the USER role by default.
-- This ensures backward compatibility.

INSERT INTO user_roles (user_id, role)
SELECT id, 'USER' FROM users
WHERE id NOT IN (SELECT user_id FROM user_roles);

COMMENT ON DATABASE linkedin_users IS 'LinkedIn-like user management system with authentication and RBAC';

-- =============================================================================
-- 4. SAMPLE DATA (Optional - for development/testing)
-- =============================================================================
-- Uncomment the following to create sample admin users

-- Create an admin user (if you want to test admin functionality)
-- Note: Password is BCrypt hash of "Admin123"
-- INSERT INTO users (
--     email, password, name, account_type, is_active, email_verified,
--     created_at, updated_at
-- ) VALUES (
--     'admin@linkedin.com',
--     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
--     'System Administrator',
--     'PREMIUM',
--     true,
--     true,
--     CURRENT_TIMESTAMP,
--     CURRENT_TIMESTAMP
-- ) ON CONFLICT (email) DO NOTHING;

-- Add ADMIN role to the admin user
-- INSERT INTO user_roles (user_id, role)
-- SELECT id, 'ADMIN' FROM users WHERE email = 'admin@linkedin.com'
-- ON CONFLICT DO NOTHING;

-- Add USER role to the admin user (admins should have both roles)
-- INSERT INTO user_roles (user_id, role)
-- SELECT id, 'USER' FROM users WHERE email = 'admin@linkedin.com'
-- ON CONFLICT DO NOTHING;

-- =============================================================================
-- 5. VERIFY MIGRATION
-- =============================================================================
-- Run these queries to verify the migration succeeded:

-- Check user_roles table structure
-- SELECT column_name, data_type, is_nullable
-- FROM information_schema.columns
-- WHERE table_name = 'user_roles'
-- ORDER BY ordinal_position;

-- Check refresh_tokens table structure
-- SELECT column_name, data_type, is_nullable
-- FROM information_schema.columns
-- WHERE table_name = 'refresh_tokens'
-- ORDER BY ordinal_position;

-- Count users with roles
-- SELECT COUNT(*) as total_users_with_roles FROM user_roles;

-- View indexes
-- SELECT tablename, indexname, indexdef
-- FROM pg_indexes
-- WHERE tablename IN ('user_roles', 'refresh_tokens')
-- ORDER BY tablename, indexname;

-- =============================================================================
-- ROLLBACK SCRIPT (DO NOT EXECUTE - FOR REFERENCE ONLY)
-- =============================================================================
-- If you need to rollback this migration manually:
-- DROP TABLE IF EXISTS refresh_tokens CASCADE;
-- DROP TABLE IF EXISTS user_roles CASCADE;

