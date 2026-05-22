CREATE TABLE contests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(160) NOT NULL,
    description MEDIUMTEXT,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    visible TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE contest_problems (
    contest_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (contest_id, problem_id),
    CONSTRAINT fk_contest_problems_contest FOREIGN KEY (contest_id) REFERENCES contests(id) ON DELETE CASCADE,
    CONSTRAINT fk_contest_problems_problem FOREIGN KEY (problem_id) REFERENCES problems(id) ON DELETE CASCADE
);

ALTER TABLE submissions ADD COLUMN contest_id BIGINT NULL;
ALTER TABLE submissions ADD CONSTRAINT fk_submissions_contest FOREIGN KEY (contest_id) REFERENCES contests(id) ON DELETE SET NULL;
