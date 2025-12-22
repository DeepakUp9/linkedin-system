-- ============================================================================
-- Flyway Migration V1: Create Posts, Comments, and Likes Tables
-- ============================================================================
-- Service: post-service
-- Database: linkedin_posts
-- Description: Creates core tables for post management including:
--              - posts: Main post content
--              - comments: Comments with nested reply support
--              - likes: Like tracking for posts and comments
-- ============================================================================

-- ============================================================================
-- Table: posts
-- ============================================================================
-- Purpose: Stores all user posts (status updates, images, articles, shares)
-- Features:
--   - Multiple post types (TEXT, IMAGE, ARTICLE, VIDEO, POLL)
--   - Visibility control (PUBLIC, CONNECTIONS_ONLY, PRIVATE)
--   - Rich text support (hashtags, mentions)
--   - Engagement tracking (likes, comments, shares, views)
--   - Soft delete support
--   - Audit fields (created_at, updated_at)
-- ============================================================================

CREATE TABLE IF NOT EXISTS posts (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,
    
    -- Author Information
    author_id BIGINT NOT NULL,
    -- NOTE: author_id references users.id from user-service database
    -- Foreign key constraint omitted for microservices independence
    -- Application-level consistency enforced via Feign client
    
    -- Post Content
    type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    -- Enum: TEXT, IMAGE, ARTICLE, VIDEO, POLL
    CONSTRAINT chk_posts_type CHECK (type IN ('TEXT', 'IMAGE', 'ARTICLE', 'VIDEO', 'POLL')),
    
    content TEXT NOT NULL,
    -- Main text content, max 3000 characters (enforced at application level)
    
    visibility VARCHAR(20) NOT NULL DEFAULT 'CONNECTIONS_ONLY',
    -- Enum: PUBLIC, CONNECTIONS_ONLY, PRIVATE
    CONSTRAINT chk_posts_visibility CHECK (visibility IN ('PUBLIC', 'CONNECTIONS_ONLY', 'PRIVATE')),
    
    -- Media Content
    image_urls TEXT,
    -- Comma-separated URLs for image posts
    -- Example: "https://s3.../img1.jpg,https://s3.../img2.jpg"
    
    -- Sharing/Repost
    shared_post_id BIGINT,
    -- ID of original post if this is a share/repost
    -- Self-referencing foreign key
    
    share_comment TEXT,
    -- Additional comment when sharing someone else's post
    
    -- Extracted Metadata
    hashtags VARCHAR(500),
    -- Comma-separated hashtags extracted from content
    -- Example: "NewJob,Excited,Google"
    
    mentions VARCHAR(500),
    -- Comma-separated user IDs mentioned in post
    -- Example: "123,456,789"
    
    -- Engagement Metrics (Denormalized for Performance)
    likes_count INTEGER NOT NULL DEFAULT 0,
    comments_count INTEGER NOT NULL DEFAULT 0,
    shares_count INTEGER NOT NULL DEFAULT 0,
    views_count INTEGER NOT NULL DEFAULT 0,
    -- NOTE: These are updated via application logic when likes/comments/shares occur
    -- Denormalization trade-off: Faster reads, application-managed consistency
    
    CONSTRAINT chk_posts_likes_count CHECK (likes_count >= 0),
    CONSTRAINT chk_posts_comments_count CHECK (comments_count >= 0),
    CONSTRAINT chk_posts_shares_count CHECK (shares_count >= 0),
    CONSTRAINT chk_posts_views_count CHECK (views_count >= 0),
    
    -- Soft Delete
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    
    -- Audit Fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Indexes for posts table
-- ============================================================================

-- Index for finding posts by author (most common query)
CREATE INDEX idx_posts_author ON posts(author_id);

-- Index for chronological feed queries
CREATE INDEX idx_posts_created ON posts(created_at DESC);

-- Index for visibility filtering
CREATE INDEX idx_posts_visibility ON posts(visibility);

-- Index for filtering out deleted posts
CREATE INDEX idx_posts_deleted ON posts(is_deleted);

-- Composite index for author's non-deleted posts (optimizes profile page)
CREATE INDEX idx_posts_author_deleted ON posts(author_id, is_deleted, created_at DESC);

-- Index for shared posts lookup
CREATE INDEX idx_posts_shared ON posts(shared_post_id) WHERE shared_post_id IS NOT NULL;

-- Index for trending posts (high engagement)
CREATE INDEX idx_posts_engagement ON posts(created_at DESC, likes_count, comments_count, shares_count) 
    WHERE is_deleted = FALSE;

-- ============================================================================
-- Foreign Key for shared posts (self-reference)
-- ============================================================================

ALTER TABLE posts 
    ADD CONSTRAINT fk_posts_shared_post 
    FOREIGN KEY (shared_post_id) 
    REFERENCES posts(id) 
    ON DELETE SET NULL;
-- ON DELETE SET NULL: If original post is deleted, share remains but loses reference

-- ============================================================================
-- Table: comments
-- ============================================================================
-- Purpose: Stores comments on posts with nested reply support
-- Features:
--   - Root-level comments (parent_comment_id = NULL)
--   - Nested replies (parent_comment_id = comment.id)
--   - Like tracking on comments
--   - Edit history tracking
--   - Soft delete support
--   - Composite Pattern for tree structure
-- ============================================================================

CREATE TABLE IF NOT EXISTS comments (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,
    
    -- Post Reference
    post_id BIGINT NOT NULL,
    -- Which post this comment is on
    
    -- Author Information
    author_id BIGINT NOT NULL,
    -- Who wrote this comment
    
    -- Comment Content
    content TEXT NOT NULL,
    -- Text content of the comment
    
    -- Nested Structure (Composite Pattern)
    parent_comment_id BIGINT,
    -- NULL for root comments, set to comment.id for replies
    -- This creates a tree structure for nested conversations
    
    -- Engagement Metrics
    likes_count INTEGER NOT NULL DEFAULT 0,
    replies_count INTEGER NOT NULL DEFAULT 0,
    
    CONSTRAINT chk_comments_likes_count CHECK (likes_count >= 0),
    CONSTRAINT chk_comments_replies_count CHECK (replies_count >= 0),
    
    -- Edit Tracking
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    edited_at TIMESTAMP,
    
    -- Soft Delete
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    
    -- Audit Fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Indexes for comments table
-- ============================================================================

-- Index for finding all comments on a post
CREATE INDEX idx_comments_post ON comments(post_id, created_at);

-- Index for finding comments by author
CREATE INDEX idx_comments_author ON comments(author_id);

-- Index for finding replies to a comment
CREATE INDEX idx_comments_parent ON comments(parent_comment_id) WHERE parent_comment_id IS NOT NULL;

-- Index for chronological ordering
CREATE INDEX idx_comments_created ON comments(created_at);

-- Index for filtering out deleted comments
CREATE INDEX idx_comments_deleted ON comments(is_deleted);

-- Composite index for root comments on a post (most common query)
CREATE INDEX idx_comments_post_root ON comments(post_id, parent_comment_id, created_at) 
    WHERE parent_comment_id IS NULL AND is_deleted = FALSE;

-- Composite index for replies to a comment
CREATE INDEX idx_comments_post_parent ON comments(post_id, parent_comment_id, created_at) 
    WHERE parent_comment_id IS NOT NULL AND is_deleted = FALSE;

-- ============================================================================
-- Foreign Keys for comments table
-- ============================================================================

-- Foreign key to posts table
ALTER TABLE comments 
    ADD CONSTRAINT fk_comments_post 
    FOREIGN KEY (post_id) 
    REFERENCES posts(id) 
    ON DELETE CASCADE;
-- ON DELETE CASCADE: If post is deleted, all comments are deleted

-- Foreign key for nested comments (self-reference)
ALTER TABLE comments 
    ADD CONSTRAINT fk_comments_parent 
    FOREIGN KEY (parent_comment_id) 
    REFERENCES comments(id) 
    ON DELETE CASCADE;
-- ON DELETE CASCADE: If parent comment deleted, replies are also deleted

-- ============================================================================
-- Table: likes
-- ============================================================================
-- Purpose: Tracks likes on posts and comments
-- Features:
--   - One like per user per post/comment (unique constraint)
--   - Supports both post likes and comment likes
--   - Simple timestamp tracking
-- ============================================================================

CREATE TABLE IF NOT EXISTS likes (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,
    
    -- User Information
    user_id BIGINT NOT NULL,
    -- Who liked
    
    -- Target Information
    post_id BIGINT NOT NULL,
    -- Which post (always set, even for comment likes)
    
    comment_id BIGINT,
    -- Which comment (NULL for post likes, set for comment likes)
    
    -- Audit Field
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Unique Constraint for likes table
-- ============================================================================
-- Ensures one like per user per target (post or comment)

ALTER TABLE likes 
    ADD CONSTRAINT uk_likes_user_post_comment 
    UNIQUE (user_id, post_id, comment_id);
-- This prevents duplicate likes: same user cannot like same post/comment twice

-- ============================================================================
-- Indexes for likes table
-- ============================================================================

-- Index for finding all likes by a user
CREATE INDEX idx_likes_user ON likes(user_id);

-- Index for finding all likes on a post
CREATE INDEX idx_likes_post ON likes(post_id);

-- Index for finding all likes on a comment
CREATE INDEX idx_likes_comment ON likes(comment_id) WHERE comment_id IS NOT NULL;

-- Index for chronological ordering
CREATE INDEX idx_likes_created ON likes(created_at DESC);

-- Composite index for checking if user liked a post
CREATE INDEX idx_likes_user_post ON likes(user_id, post_id, comment_id);

-- ============================================================================
-- Foreign Keys for likes table
-- ============================================================================

-- Foreign key to posts table
ALTER TABLE likes 
    ADD CONSTRAINT fk_likes_post 
    FOREIGN KEY (post_id) 
    REFERENCES posts(id) 
    ON DELETE CASCADE;
-- ON DELETE CASCADE: If post is deleted, all likes are deleted

-- Foreign key to comments table (optional)
ALTER TABLE likes 
    ADD CONSTRAINT fk_likes_comment 
    FOREIGN KEY (comment_id) 
    REFERENCES comments(id) 
    ON DELETE CASCADE;
-- ON DELETE CASCADE: If comment is deleted, all likes are deleted

-- ============================================================================
-- Triggers for Auto-updating updated_at timestamp
-- ============================================================================
-- Purpose: Automatically set updated_at to CURRENT_TIMESTAMP on UPDATE
-- This is a common pattern for audit trails

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for posts table
CREATE TRIGGER trg_posts_updated_at
    BEFORE UPDATE ON posts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Trigger for comments table
CREATE TRIGGER trg_comments_updated_at
    BEFORE UPDATE ON comments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- Comments and Documentation
-- ============================================================================

COMMENT ON TABLE posts IS 'Stores all user posts including status updates, images, articles, and shares';
COMMENT ON COLUMN posts.author_id IS 'ID of user who created the post (references user-service)';
COMMENT ON COLUMN posts.type IS 'Type of post: TEXT, IMAGE, ARTICLE, VIDEO, POLL';
COMMENT ON COLUMN posts.visibility IS 'Who can see this post: PUBLIC, CONNECTIONS_ONLY, PRIVATE';
COMMENT ON COLUMN posts.shared_post_id IS 'ID of original post if this is a repost/share';
COMMENT ON COLUMN posts.likes_count IS 'Denormalized count of likes (updated by application)';
COMMENT ON COLUMN posts.is_deleted IS 'Soft delete flag (TRUE = deleted but retained for audit)';

COMMENT ON TABLE comments IS 'Stores comments on posts with support for nested replies';
COMMENT ON COLUMN comments.parent_comment_id IS 'NULL for root comments, set to comment.id for replies (Composite Pattern)';
COMMENT ON COLUMN comments.is_edited IS 'TRUE if comment has been edited after creation';

COMMENT ON TABLE likes IS 'Tracks likes on posts and comments with one-like-per-user constraint';
COMMENT ON COLUMN likes.comment_id IS 'NULL for post likes, set for comment likes';

-- ============================================================================
-- Sample Queries for Reference
-- ============================================================================

-- Find all posts by a user (excluding deleted):
-- SELECT * FROM posts WHERE author_id = ? AND is_deleted = FALSE ORDER BY created_at DESC;

-- Find all root comments on a post:
-- SELECT * FROM comments WHERE post_id = ? AND parent_comment_id IS NULL AND is_deleted = FALSE ORDER BY created_at;

-- Find all replies to a comment:
-- SELECT * FROM comments WHERE parent_comment_id = ? AND is_deleted = FALSE ORDER BY created_at;

-- Check if user liked a post:
-- SELECT EXISTS(SELECT 1 FROM likes WHERE user_id = ? AND post_id = ? AND comment_id IS NULL);

-- Get trending posts (high engagement in last 24 hours):
-- SELECT * FROM posts 
-- WHERE is_deleted = FALSE 
-- AND created_at > NOW() - INTERVAL '24 hours'
-- ORDER BY (likes_count + comments_count * 2 + shares_count * 3) DESC
-- LIMIT 10;

-- ============================================================================
-- End of Migration V1
-- ============================================================================

