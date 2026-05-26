CREATE TABLE system_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    level VARCHAR(16) NOT NULL,
    service VARCHAR(32) NOT NULL,
    module VARCHAR(64) NOT NULL,
    event VARCHAR(64) NOT NULL,
    message VARCHAR(512) NOT NULL,
    submission_id BIGINT NULL,
    problem_id BIGINT NULL,
    user_id BIGINT NULL,
    details TEXT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_system_logs_created_at (created_at),
    INDEX idx_system_logs_level_created_at (level, created_at),
    INDEX idx_system_logs_service_created_at (service, created_at),
    INDEX idx_system_logs_submission_created_at (submission_id, created_at)
);
