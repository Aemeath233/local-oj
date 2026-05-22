CREATE TABLE problem_tags (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL UNIQUE,
    color VARCHAR(32),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE llm_settings (
    id BIGINT PRIMARY KEY,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    base_url VARCHAR(512),
    model VARCHAR(128),
    api_key VARCHAR(1024),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO problem_tags (name, color) VALUES
('基础', '#409EFF'),
('模拟', '#67C23A'),
('数学', '#E6A23C'),
('字符串', '#909399'),
('数据结构', '#F56C6C'),
('贪心', '#2F9E44'),
('动态规划', '#8E44AD'),
('图论', '#1F78D1');

INSERT INTO llm_settings (id, enabled, base_url, model, api_key)
VALUES (1, 0, '', '', '');
