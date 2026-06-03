-- Create table to store leaderboard rank snapshots
CREATE TABLE user_rank_snapshots (
    user_id BIGINT PRIMARY KEY,
    prev_rank INT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_rank_snapshots_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
