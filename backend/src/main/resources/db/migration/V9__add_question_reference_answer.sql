ALTER TABLE questions ADD COLUMN reference_answer VARCHAR(4000);

UPDATE questions
SET reference_answer = (
    SELECT ao.option_text
    FROM answer_options ao
    WHERE ao.question_id = questions.id AND ao.is_correct = TRUE
)
WHERE question_type = 'SHORT_ANSWER';

DELETE FROM answer_options
WHERE question_id IN (SELECT id FROM questions WHERE question_type = 'SHORT_ANSWER');
