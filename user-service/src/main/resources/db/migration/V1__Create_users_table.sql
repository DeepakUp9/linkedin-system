-- ============================================
-- Flyway Migration V1: Create Users Table
-- ============================================
-- Description: Creates the main users table with all fields from User entity
-- Author: LinkedIn System
-- Date: 2025-12-20
-- ============================================

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,
    
    -- Authentication & Identity
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    
    -- Basic Profile Information
    name VARCHAR(255) NOT NULL,
    headline VARCHAR(255),
    summary TEXT,
    location VARCHAR(255),
    profile_photo_url VARCHAR(512),
    
    -- Account Management
    account_type VARCHAR(20) NOT NULL DEFAULT 'BASIC',
    is_active BOOLEAN NOT NULL DEFAULT true,
    email_verified BOOLEAN NOT NULL DEFAULT false,
    
    -- Optional Contact & Personal Information
    phone_number VARCHAR(20),
    date_of_birth DATE,
    
    -- Professional Information
    industry VARCHAR(100),
    current_job_title VARCHAR(255),
    current_company VARCHAR(255),
    
    -- Audit Fields (from BaseAuditEntity)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    
    -- Constraints
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_account_type CHECK (account_type IN ('BASIC', 'PREMIUM'))
);

-- Create indexes for performance optimization
-- Index on email for fast login lookups
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Index on account_type for filtering premium/basic users
CREATE INDEX IF NOT EXISTS idx_users_account_type ON users(account_type);

-- Index on is_active for filtering active users
CREATE INDEX IF NOT EXISTS idx_users_is_active ON users(is_active);

-- Index on created_at for sorting by registration date
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at DESC);

-- Composite index for common query: active users by account type
CREATE INDEX IF NOT EXISTS idx_users_active_account_type ON users(is_active, account_type);

-- Index on name for search functionality (case-insensitive)
CREATE INDEX IF NOT EXISTS idx_users_name_lower ON users(LOWER(name));

-- ============================================
-- Comments for documentation
-- ============================================

COMMENT ON TABLE users IS 'Main users table storing user accounts and profiles';

COMMENT ON COLUMN users.id IS 'Auto-generated unique identifier';
COMMENT ON COLUMN users.email IS 'User email address (used for login, must be unique)';
COMMENT ON COLUMN users.password IS 'BCrypt hashed password (never store plain text)';
COMMENT ON COLUMN users.name IS 'User full name';
COMMENT ON COLUMN users.headline IS 'Professional headline (e.g., "Software Engineer at Google")';
COMMENT ON COLUMN users.summary IS 'Professional summary/bio';
COMMENT ON COLUMN users.account_type IS 'Account tier: BASIC (free) or PREMIUM (paid)';
COMMENT ON COLUMN users.is_active IS 'Account status: true = active, false = deactivated/suspended';
COMMENT ON COLUMN users.email_verified IS 'Email verification status: true = verified, false = pending';
COMMENT ON COLUMN users.created_at IS 'Timestamp when user account was created';
COMMENT ON COLUMN users.updated_at IS 'Timestamp when user account was last modified';
COMMENT ON COLUMN users.created_by IS 'Username/email of user who created this account';
COMMENT ON COLUMN users.updated_by IS 'Username/email of user who last modified this account';

-- ============================================
-- Insert sample data for development/testing
-- (Only for development environment)
-- ============================================

-- Note: In production, this section would be removed or conditional
-- Password: "password123" hashed with BCrypt
INSERT INTO users (email, password, name, headline, account_type, is_active, email_verified, created_at, updated_at) VALUES
('john.doe@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'John Doe', 'Software Engineer at Google', 'PREMIUM', true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('jane.smith@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Jane Smith', 'Product Manager at Meta', 'PREMIUM', true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('bob.johnson@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Bob Johnson', 'Data Scientist', 'BASIC', true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('alice.williams@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Alice Williams', 'UX Designer', 'BASIC', true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('charlie.brown@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Charlie Brown', 'Marketing Manager', 'BASIC', false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================
-- End of Migration V1
-- ============================================

