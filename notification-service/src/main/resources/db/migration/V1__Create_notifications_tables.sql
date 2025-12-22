-- V1__Create_notifications_tables.sql

-- Create notifications table
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    action_link VARCHAR(500),
    icon_url VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    related_entity_id VARCHAR(100),
    related_entity_type VARCHAR(50),
    metadata TEXT,
    sent_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create notification_preferences table
CREATE TABLE notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_prefs_user_type UNIQUE (user_id, notification_type)
);

-- Create indexes for notifications
CREATE INDEX idx_notifications_user ON notifications (user_id);
CREATE INDEX idx_notifications_user_status ON notifications (user_id, status);
CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read);
CREATE INDEX idx_notifications_created ON notifications (created_at);
CREATE INDEX idx_notifications_type ON notifications (type);

-- Create indexes for preferences
CREATE INDEX idx_prefs_user ON notification_preferences (user_id);

-- Function to update updated_at column
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for updated_at
CREATE TRIGGER trigger_notifications_updated_at
    BEFORE UPDATE ON notifications
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_preferences_updated_at
    BEFORE UPDATE ON notification_preferences
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments
COMMENT ON TABLE notifications IS 'Stores all notifications sent to users';
COMMENT ON TABLE notification_preferences IS 'User preferences for notification channels';

