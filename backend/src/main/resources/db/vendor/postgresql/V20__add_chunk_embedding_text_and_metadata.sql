-- Legacy chunks remain readable; new ingestions persist the contextual text sent to the adapter.
ALTER TABLE document_chunks ADD COLUMN embedding_text TEXT;
ALTER TABLE document_chunks ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
