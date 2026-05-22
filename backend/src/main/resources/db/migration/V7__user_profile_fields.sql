ALTER TABLE users
    ADD COLUMN avatar_url VARCHAR(512) NULL AFTER display_name,
    ADD COLUMN student_no VARCHAR(64) NULL AFTER avatar_url,
    ADD COLUMN major VARCHAR(128) NULL AFTER student_no,
    ADD UNIQUE KEY uk_users_student_no (student_no);
