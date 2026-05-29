CREATE TABLE system_settings (
    id BIGINT PRIMARY KEY,
    allowed_origins TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO system_settings (id, allowed_origins)
VALUES (1, 'http://localhost:5173,http://127.0.0.1:5173,http://192.168.*.*:5173,http://10.*.*.*:5173');
