CREATE TABLE contest_registrations (
    contest_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    registered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (contest_id, user_id),
    CONSTRAINT fk_contest_registrations_contest FOREIGN KEY (contest_id) REFERENCES contests(id) ON DELETE CASCADE,
    CONSTRAINT fk_contest_registrations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_contest_registrations_user (user_id, registered_at)
);

CREATE TABLE contest_problem_visibility_locks (
    contest_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (contest_id, problem_id),
    CONSTRAINT fk_contest_problem_visibility_locks_contest FOREIGN KEY (contest_id) REFERENCES contests(id) ON DELETE CASCADE,
    CONSTRAINT fk_contest_problem_visibility_locks_problem FOREIGN KEY (problem_id) REFERENCES problems(id) ON DELETE CASCADE,
    INDEX idx_contest_problem_visibility_locks_problem (problem_id)
);

CREATE TABLE sandbox_settings (
    id BIGINT PRIMARY KEY,
    worker_threads INT NOT NULL DEFAULT 1,
    max_concurrent_runs INT NOT NULL DEFAULT 1,
    compile_timeout_ms INT NOT NULL DEFAULT 10000,
    default_output_limit_kb INT NOT NULL DEFAULT 1024,
    max_process_count INT NOT NULL DEFAULT 50,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO sandbox_settings (
    id,
    worker_threads,
    max_concurrent_runs,
    compile_timeout_ms,
    default_output_limit_kb,
    max_process_count
) VALUES (1, 1, 1, 10000, 1024, 50);
