ALTER TABLE documents ADD COLUMN ingestion_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUESTED';
ALTER TABLE documents ADD COLUMN ingestion_error VARCHAR(50);
ALTER TABLE documents ADD COLUMN published_ingestion_id VARCHAR(36);
ALTER TABLE documents ADD CONSTRAINT chk_document_ingestion_status
    CHECK (ingestion_status IN ('NOT_REQUESTED','PENDING','PROCESSING','INDEXED','FAILED'));

CREATE TABLE document_ingestions (
    id VARCHAR(36) PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    requested_by BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    active_slot INTEGER,
    attempts INTEGER NOT NULL DEFAULT 0,
    claim_token VARCHAR(36),
    lease_until TIMESTAMP,
    error_code VARCHAR(50),
    embedding_model VARCHAR(255),
    embedding_dimensions INTEGER,
    chunk_count INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ingestion_document UNIQUE(id,document_id),
    CONSTRAINT uq_active_document_ingestion UNIQUE(document_id,active_slot),
    CONSTRAINT chk_ingestion_status CHECK(status IN ('PENDING','PROCESSING','INDEXED','FAILED')),
    CONSTRAINT chk_ingestion_attempts CHECK(attempts >= 0),
    CONSTRAINT chk_ingestion_slot CHECK(
        (status IN ('PENDING','PROCESSING') AND active_slot IS NOT NULL AND active_slot=1)
        OR (status IN ('INDEXED','FAILED') AND active_slot IS NULL)),
    CONSTRAINT chk_ingestion_claim CHECK(status <> 'PROCESSING' OR (claim_token IS NOT NULL AND lease_until IS NOT NULL))
);
CREATE INDEX idx_ingestion_pending ON document_ingestions(status,lease_until,created_at);
ALTER TABLE documents ADD CONSTRAINT fk_document_published_ingestion
    FOREIGN KEY(published_ingestion_id,id) REFERENCES document_ingestions(id,document_id);
