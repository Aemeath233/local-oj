UPDATE problems
SET description = CONCAT(
        SUBSTRING_INDEX(description, '## ??', 1),
        '## Input',
        SUBSTRING_INDEX(SUBSTRING_INDEX(description, '## ??', 2), '## ??', -1),
        '## Output',
        SUBSTRING_INDEX(description, '## ??', -1)
    )
WHERE (LENGTH(description) - LENGTH(REPLACE(description, '## ??', ''))) / LENGTH('## ??') >= 2;

UPDATE problems
SET description = REPLACE(description, '## ??', '## Input')
WHERE description LIKE '%## ??%';
