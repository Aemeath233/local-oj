ALTER TABLE users
    ADD COLUMN email VARCHAR(190) NULL AFTER username,
    ADD UNIQUE KEY uk_users_email (email);

CREATE TABLE smtp_settings (
    id BIGINT PRIMARY KEY,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    host VARCHAR(255),
    port INT NOT NULL DEFAULT 587,
    username VARCHAR(255),
    password VARCHAR(255),
    from_address VARCHAR(255),
    from_name VARCHAR(128),
    auth_enabled TINYINT(1) NOT NULL DEFAULT 1,
    use_ssl TINYINT(1) NOT NULL DEFAULT 0,
    use_starttls TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO smtp_settings (id, enabled, port, auth_enabled, use_ssl, use_starttls)
VALUES (1, 0, 587, 1, 0, 1);

CREATE TABLE email_verification_codes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(190) NOT NULL,
    code VARCHAR(16) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    expires_at DATETIME NOT NULL,
    consumed TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_email_verification_lookup (email, purpose, consumed, expires_at)
);
