CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE problems (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    slug VARCHAR(96) NOT NULL UNIQUE,
    title VARCHAR(160) NOT NULL,
    description MEDIUMTEXT NOT NULL,
    time_limit_ms INT NOT NULL DEFAULT 1000,
    memory_limit_kb INT NOT NULL DEFAULT 262144,
    difficulty VARCHAR(32) NOT NULL DEFAULT 'Easy',
    tags VARCHAR(255),
    visible TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE test_cases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    problem_id BIGINT NOT NULL,
    case_name VARCHAR(160),
    input_file VARCHAR(255),
    output_file VARCHAR(255),
    input_size BIGINT NOT NULL DEFAULT 0,
    output_size BIGINT NOT NULL DEFAULT 0,
    input_text MEDIUMTEXT,
    expected_output MEDIUMTEXT,
    score INT NOT NULL DEFAULT 100,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_test_cases_problem FOREIGN KEY (problem_id) REFERENCES problems(id) ON DELETE CASCADE,
    INDEX idx_test_cases_problem (problem_id, sort_order)
);

CREATE TABLE submissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    language VARCHAR(32) NOT NULL,
    source_code MEDIUMTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    verdict VARCHAR(32),
    score INT NOT NULL DEFAULT 0,
    time_ms BIGINT,
    memory_kb BIGINT,
    error_message MEDIUMTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    judged_at DATETIME,
    CONSTRAINT fk_submissions_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_submissions_problem FOREIGN KEY (problem_id) REFERENCES problems(id),
    INDEX idx_submissions_user_created (user_id, created_at),
    INDEX idx_submissions_problem_created (problem_id, created_at)
);

CREATE TABLE submission_case_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    test_case_id BIGINT,
    case_index INT NOT NULL,
    verdict VARCHAR(32) NOT NULL,
    time_ms BIGINT,
    memory_kb BIGINT,
    stdout_text MEDIUMTEXT,
    stderr_text MEDIUMTEXT,
    message MEDIUMTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_case_results_submission FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE,
    CONSTRAINT fk_case_results_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id) ON DELETE SET NULL,
    INDEX idx_case_results_submission (submission_id, case_index)
);
