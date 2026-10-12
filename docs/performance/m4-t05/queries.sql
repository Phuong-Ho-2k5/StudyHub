-- A_history
EXPLAIN (ANALYZE, BUFFERS) SELECT a.*, q.* FROM quiz_attempts a
 LEFT JOIN quizzes q ON q.id = a.quiz_id WHERE a.user_id = 2
 ORDER BY a.created_at DESC, a.id DESC LIMIT 10 OFFSET 0;

-- B_study_statistics
EXPLAIN (ANALYZE, BUFFERS) SELECT COUNT(id), COALESCE(SUM(duration_seconds), 0)
 FROM study_sessions WHERE user_id = 2 AND end_time IS NOT NULL;

-- C_quiz_statistics
EXPLAIN (ANALYZE, BUFFERS) SELECT COUNT(id), COALESCE(AVG(question_percentage), 0)
 FROM quiz_attempts WHERE user_id = 2;

-- D_history_count
EXPLAIN (ANALYZE, BUFFERS) SELECT COUNT(id) FROM quiz_attempts WHERE user_id = 2;
