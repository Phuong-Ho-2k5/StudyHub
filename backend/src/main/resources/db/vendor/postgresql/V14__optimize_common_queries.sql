CREATE INDEX idx_quiz_attempts_user_created_id
    ON quiz_attempts (user_id, created_at DESC, id DESC);

CREATE INDEX idx_study_sessions_completed_user
    ON study_sessions (user_id)
    WHERE end_time IS NOT NULL;
