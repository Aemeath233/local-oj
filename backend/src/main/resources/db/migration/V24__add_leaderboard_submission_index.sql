-- Add index on user_id, contest_id, verdict to optimize leaderboard query performance
CREATE INDEX idx_submissions_leaderboard ON submissions(user_id, contest_id, verdict);
