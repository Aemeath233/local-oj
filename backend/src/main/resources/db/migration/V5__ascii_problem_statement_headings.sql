UPDATE problems
SET description = REPLACE(
        REPLACE(
            description,
            CONCAT('## ', CONVERT(UNHEX('E8BE93E585A5') USING utf8mb4)),
            '## Input'
        ),
        CONCAT('## ', CONVERT(UNHEX('E8BE93E587BA') USING utf8mb4)),
        '## Output'
    );
