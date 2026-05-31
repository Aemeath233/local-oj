-- Add index on contest_id and id to optimize contest list queries
CREATE INDEX idx_submissions_contest_id ON submissions(contest_id, id DESC);

-- Add index on status to optimize requeueUnfinished filtering
CREATE INDEX idx_submissions_status ON submissions(status);
