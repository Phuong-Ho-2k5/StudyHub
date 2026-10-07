ALTER TABLE quiz_attempts ALTER COLUMN question_percentage TYPE DOUBLE PRECISION;

CREATE INDEX idx_quiz_attempts_user_quiz_created_at ON quiz_attempts (user_id, quiz_id, created_at);
