CREATE TABLE problem_tag_relation (
    problem_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (problem_id, tag_id),
    CONSTRAINT fk_ptr_problem FOREIGN KEY (problem_id) REFERENCES problems(id) ON DELETE CASCADE,
    CONSTRAINT fk_ptr_tag FOREIGN KEY (tag_id) REFERENCES problem_tags(id) ON DELETE CASCADE
);

CREATE TEMPORARY TABLE tmp_problem_tags (
    problem_id BIGINT NOT NULL,
    tag_name VARCHAR(64) NOT NULL
);

INSERT INTO tmp_problem_tags (problem_id, tag_name)
WITH RECURSIVE split_tags AS (
    SELECT
        id AS problem_id,
        TRIM(SUBSTRING_INDEX(normalized_tags, ',', 1)) AS tag_name,
        CASE
            WHEN LOCATE(',', normalized_tags) = 0 THEN ''
            ELSE SUBSTRING(normalized_tags, LOCATE(',', normalized_tags) + 1)
        END AS rest
    FROM (
        SELECT id, REPLACE(tags, '，', ',') AS normalized_tags
        FROM problems
        WHERE tags IS NOT NULL AND TRIM(tags) != ''
    ) p
    UNION ALL
    SELECT
        problem_id,
        TRIM(SUBSTRING_INDEX(rest, ',', 1)) AS tag_name,
        CASE
            WHEN LOCATE(',', rest) = 0 THEN ''
            ELSE SUBSTRING(rest, LOCATE(',', rest) + 1)
        END AS rest
    FROM split_tags
    WHERE rest != ''
)
SELECT DISTINCT problem_id, tag_name
FROM split_tags
WHERE tag_name != '';

INSERT IGNORE INTO problem_tags (name, color)
SELECT DISTINCT tag_name, '#909399'
FROM tmp_problem_tags;

INSERT IGNORE INTO problem_tag_relation (problem_id, tag_id)
SELECT tmp.problem_id, t.id
FROM tmp_problem_tags tmp
JOIN problem_tags t ON t.name = tmp.tag_name;

DROP TEMPORARY TABLE tmp_problem_tags;

-- Drop the legacy column
ALTER TABLE problems DROP COLUMN tags;
