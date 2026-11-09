CREATE TABLE ai_jobs (
    id VARCHAR(36) PRIMARY KEY,
    requested_by BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    document_id BIGINT REFERENCES documents(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    claim_token VARCHAR(36),
    result_type VARCHAR(40),
    result_id VARCHAR(100),
    error_code VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_ai_job_type CHECK (type IN ('INGEST_DOCUMENT','EXTRACT_CONCEPTS','GENERATE_QUIZ')),
    CONSTRAINT chk_ai_job_document CHECK (type <> 'INGEST_DOCUMENT' OR document_id IS NOT NULL),
    CONSTRAINT chk_ai_job_result CHECK (result_type IS NULL OR
        (type='INGEST_DOCUMENT' AND result_type='DOCUMENT_INDEX') OR
        (type='EXTRACT_CONCEPTS' AND result_type='CONCEPT_SUGGESTION_BATCH') OR
        (type='GENERATE_QUIZ' AND result_type='QUIZ')),
    -- Match Java Character.isWhitespace so stored references remain readable.
    CONSTRAINT chk_ai_job_result_id CHECK (result_id IS NULL OR length(trim(translate(result_id,
        CHR(9) || CHR(10) || CHR(11) || CHR(12) || CHR(13) || CHR(28) || CHR(29) || CHR(30) || CHR(31) || CHR(32) || CHR(5760) || CHR(8192) || CHR(8193) || CHR(8194) || CHR(8195) || CHR(8196) || CHR(8197) || CHR(8198) ||
        CHR(8200) || CHR(8201) || CHR(8202) || CHR(8232) || CHR(8233) || CHR(8287) || CHR(12288), REPEAT(' ',25))))>0),
    CONSTRAINT chk_ai_job_error CHECK (error_code IS NULL OR error_code IN
        ('AI_UNAVAILABLE','AI_TIMEOUT','AI_INVALID_RESPONSE','NO_CONTENT','SOURCE_CHANGED','JOB_FAILED')),
    CONSTRAINT chk_ai_job_times CHECK (updated_at>=created_at AND
        (started_at IS NULL OR started_at>=created_at) AND
        (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at>=started_at))),
    CONSTRAINT chk_ai_job_state CHECK (
        (status='PENDING' AND claim_token IS NULL AND result_type IS NULL AND result_id IS NULL
            AND error_code IS NULL AND started_at IS NULL AND finished_at IS NULL) OR
        (status='PROCESSING' AND claim_token IS NOT NULL AND started_at IS NOT NULL
            AND result_type IS NULL AND result_id IS NULL AND error_code IS NULL AND finished_at IS NULL) OR
        (status='COMPLETED' AND result_type IS NOT NULL AND result_id IS NOT NULL
            AND started_at IS NOT NULL AND finished_at IS NOT NULL AND claim_token IS NULL AND error_code IS NULL) OR
        (status='FAILED' AND error_code IS NOT NULL AND started_at IS NOT NULL AND finished_at IS NOT NULL
            AND claim_token IS NULL AND result_type IS NULL AND result_id IS NULL))
);
CREATE INDEX idx_ai_jobs_scope ON ai_jobs(requested_by,course_id,created_at,id);
CREATE INDEX idx_ai_jobs_pending ON ai_jobs(status,created_at,id);
