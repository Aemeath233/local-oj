UPDATE problems
SET description = CONCAT_WS(
        '\n\n',
        NULLIF(TRIM(TRAILING '\n' FROM description), ''),
        IF(NULLIF(TRIM(input_description), '') IS NULL, NULL, CONCAT('## 输入\n\n', TRIM(input_description))),
        IF(NULLIF(TRIM(output_description), '') IS NULL, NULL, CONCAT('## 输出\n\n', TRIM(output_description)))
    )
WHERE NULLIF(TRIM(input_description), '') IS NOT NULL
   OR NULLIF(TRIM(output_description), '') IS NOT NULL;

UPDATE problems
SET input_description = NULL,
    output_description = NULL,
    sample_input = NULL,
    sample_output = NULL;
