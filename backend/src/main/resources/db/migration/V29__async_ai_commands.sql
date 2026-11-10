CREATE TABLE ai_job_commands (
 job_id VARCHAR(36) PRIMARY KEY REFERENCES ai_jobs(id) ON DELETE CASCADE,
 schema_version INTEGER NOT NULL DEFAULT 1 CHECK(schema_version=1),
 mode VARCHAR(30) NOT NULL CHECK(mode IN ('CONCEPTS','DOCUMENT_QUIZ','COURSE_QUIZ')),
 request_json TEXT NOT NULL,
 sources_json TEXT NOT NULL,
 requested_by BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 request_key VARCHAR(100), fingerprint VARCHAR(64),
 CONSTRAINT uq_ai_command_key UNIQUE(requested_by,request_key)
);
ALTER TABLE document_ingestions ADD COLUMN ai_job_id VARCHAR(36) REFERENCES ai_jobs(id) ON DELETE SET NULL;
ALTER TABLE document_ingestions ADD COLUMN ai_claim_token VARCHAR(36);
CREATE UNIQUE INDEX uq_ingestion_ai_job ON document_ingestions(ai_job_id);
-- Run with old workers stopped. Existing ingestion IDs and published index FKs are preserved.
INSERT INTO ai_jobs(id,requested_by,course_id,document_id,type,status,claim_token,created_at,updated_at,started_at)
 SELECT i.id,i.requested_by,d.course_id,d.id,'INGEST_DOCUMENT',i.status,
 CASE WHEN i.status='PROCESSING' THEN i.claim_token ELSE NULL END,
 i.created_at,i.updated_at,CASE WHEN i.status='PROCESSING' THEN i.created_at ELSE NULL END
 FROM document_ingestions i JOIN documents d ON d.id=i.document_id
 WHERE i.status IN ('PENDING','PROCESSING');
UPDATE document_ingestions SET ai_job_id=id,ai_claim_token=claim_token WHERE status IN ('PENDING','PROCESSING');
