ALTER TABLE users ADD COLUMN username_change_count_current_month INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN last_username_changed_at DATETIME NULL;
