CREATE TABLE plagiarism_checks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    language_family VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    max_similarity DOUBLE,
    error_message TEXT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_contest_problem_lang (contest_id, problem_id, language_family)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
