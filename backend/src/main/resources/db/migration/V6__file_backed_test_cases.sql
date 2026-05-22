ALTER TABLE test_cases
    MODIFY input_text MEDIUMTEXT NULL,
    MODIFY expected_output MEDIUMTEXT NULL,
    ADD COLUMN case_name VARCHAR(160) NULL AFTER problem_id,
    ADD COLUMN input_file VARCHAR(255) NULL AFTER case_name,
    ADD COLUMN output_file VARCHAR(255) NULL AFTER input_file,
    ADD COLUMN input_size BIGINT NOT NULL DEFAULT 0 AFTER output_file,
    ADD COLUMN output_size BIGINT NOT NULL DEFAULT 0 AFTER input_size;
