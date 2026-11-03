ALTER TABLE quizzes DROP CONSTRAINT chk_quizzes_status;
ALTER TABLE quizzes ADD CONSTRAINT chk_quizzes_status CHECK (
    status IN ('DRAFT', 'CONFIRMED', 'PUBLISHED', 'ARCHIVED')
    AND (status <> 'CONFIRMED' OR source_type = 'AI_GENERATED')
);
ALTER TABLE quizzes ADD COLUMN content_revision BIGINT NOT NULL DEFAULT 0 CHECK (content_revision >= 0);
-- Reviewers are the owning user; their data follows the existing Workspace owner cascade.
ALTER TABLE quizzes ADD COLUMN confirmed_by BIGINT REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE quizzes ADD COLUMN confirmed_at TIMESTAMP;
ALTER TABLE quizzes ADD COLUMN published_at TIMESTAMP;
ALTER TABLE quizzes ADD CONSTRAINT chk_quiz_confirmation CHECK (
    (confirmed_by IS NULL AND confirmed_at IS NULL) OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
);
ALTER TABLE questions ADD COLUMN reviewed_by BIGINT REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE questions ADD COLUMN reviewed_at TIMESTAMP;
ALTER TABLE questions ADD CONSTRAINT chk_question_review CHECK (
    (reviewed_by IS NULL AND reviewed_at IS NULL) OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
);
