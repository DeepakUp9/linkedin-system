-- ============================================================================
-- Flyway Migration: V1__Create_connections_table.sql
-- Description: Initial schema for connection-service
-- Author: LinkedIn System Team
-- Created: 2025-12-21
-- ============================================================================

-- ============================================================================
-- Table: connections
-- Purpose: Stores professional connections between users
-- ============================================================================
CREATE TABLE connections (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,
    
    -- Core Fields: Who is connecting with whom?
    requester_id BIGINT NOT NULL,
    addressee_id BIGINT NOT NULL,
    
    -- State Management
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    
    -- Timestamps: When did things happen?
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    
    -- Optional Message
    message TEXT,
    
    -- Audit Fields: Automatic tracking
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- ========================================================================
    -- Constraints
    -- ========================================================================
    
    -- Constraint 1: Cannot connect to yourself
    CONSTRAINT chk_connections_different_users CHECK (requester_id != addressee_id),
    
    -- Constraint 2: State must be one of the valid values
    CONSTRAINT chk_connections_valid_state CHECK (state IN ('PENDING', 'ACCEPTED', 'REJECTED', 'BLOCKED')),
    
    -- Constraint 3: Prevent duplicate connection requests
    -- (User A can only send one request to User B at a time)
    CONSTRAINT uk_connections_requester_addressee UNIQUE (requester_id, addressee_id)
);

-- ============================================================================
-- Indexes: Optimize query performance
-- ============================================================================

-- Index 1: Fast lookup by requester (e.g., "Show all my sent requests")
CREATE INDEX idx_connections_requester ON connections (requester_id);

-- Index 2: Fast lookup by addressee (e.g., "Show all requests I received")
CREATE INDEX idx_connections_addressee ON connections (addressee_id);

-- Index 3: Fast filtering by state (e.g., "Show all PENDING connections")
CREATE INDEX idx_connections_state ON connections (state);

-- Index 4: Composite index for bidirectional lookup
-- (e.g., "Are User A and User B connected?")
CREATE INDEX idx_connections_requester_addressee ON connections (requester_id, addressee_id);

-- Index 5: Fast lookup for pending requests to a specific user
CREATE INDEX idx_connections_addressee_state ON connections (addressee_id, state);

-- Index 6: Fast cleanup of expired rejected/blocked connections
CREATE INDEX idx_connections_state_created ON connections (state, created_at);

-- ============================================================================
-- Comments: Documentation in the database
-- ============================================================================

COMMENT ON TABLE connections IS 'Stores professional connections between users';
COMMENT ON COLUMN connections.id IS 'Primary key - unique identifier for the connection';
COMMENT ON COLUMN connections.requester_id IS 'ID of the user who sent the connection request';
COMMENT ON COLUMN connections.addressee_id IS 'ID of the user who received the connection request';
COMMENT ON COLUMN connections.state IS 'Current state: PENDING, ACCEPTED, REJECTED, or BLOCKED';
COMMENT ON COLUMN connections.requested_at IS 'Timestamp when the connection request was sent';
COMMENT ON COLUMN connections.responded_at IS 'Timestamp when the addressee responded (accepted/rejected/blocked)';
COMMENT ON COLUMN connections.message IS 'Optional message from requester explaining why they want to connect';
COMMENT ON COLUMN connections.created_at IS 'Timestamp when this record was created (audit field)';
COMMENT ON COLUMN connections.updated_at IS 'Timestamp when this record was last updated (audit field)';

-- ============================================================================
-- Trigger: Automatically update updated_at timestamp
-- ============================================================================

-- Function to update the updated_at column
CREATE OR REPLACE FUNCTION update_connections_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger that calls the function before every UPDATE
CREATE TRIGGER trigger_connections_updated_at
    BEFORE UPDATE ON connections
    FOR EACH ROW
    EXECUTE FUNCTION update_connections_updated_at();

-- ============================================================================
-- Sample Data (Optional - for development/testing)
-- ============================================================================

-- Note: In production, you would NOT include sample data in migrations.
-- This is useful for local development and testing.
-- Uncomment the following lines if you want sample data:

/*
-- Sample connections (assuming users with IDs 1, 2, 3, 4 exist in user-service)

-- Connection 1: User 1 sent request to User 2 (PENDING)
INSERT INTO connections (requester_id, addressee_id, state, requested_at, message, created_at, updated_at)
VALUES (1, 2, 'PENDING', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'Would love to connect!', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Connection 2: User 1 and User 3 are connected (ACCEPTED)
INSERT INTO connections (requester_id, addressee_id, state, requested_at, responded_at, message, created_at, updated_at)
VALUES (1, 3, 'ACCEPTED', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '12 hours', 'Great meeting you at the conference!', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Connection 3: User 2 sent request to User 4 (ACCEPTED)
INSERT INTO connections (requester_id, addressee_id, state, requested_at, responded_at, created_at, updated_at)
VALUES (2, 4, 'ACCEPTED', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Connection 4: User 3 sent request to User 4 (REJECTED)
INSERT INTO connections (requester_id, addressee_id, state, requested_at, responded_at, created_at, updated_at)
VALUES (3, 4, 'REJECTED', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Connection 5: User 4 blocked User 1 (BLOCKED)
INSERT INTO connections (requester_id, addressee_id, state, requested_at, responded_at, message, created_at, updated_at)
VALUES (4, 1, 'BLOCKED', CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '6 days', 'Spam request', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
*/

-- ============================================================================
-- Verification Queries (Optional - run manually after migration)
-- ============================================================================

-- Query 1: Count connections by state
-- SELECT state, COUNT(*) FROM connections GROUP BY state;

-- Query 2: Show all pending connections
-- SELECT * FROM connections WHERE state = 'PENDING' ORDER BY requested_at DESC;

-- Query 3: Show all active connections for a user (e.g., user_id = 1)
-- SELECT * FROM connections 
-- WHERE (requester_id = 1 OR addressee_id = 1) 
--   AND state = 'ACCEPTED';

-- Query 4: Check for orphaned connections (users that don't exist)
-- This would require a foreign key to user-service database, which we don't have
-- in a microservices architecture. Instead, use a scheduled job to verify.

-- ============================================================================
-- Migration Complete
-- ============================================================================

