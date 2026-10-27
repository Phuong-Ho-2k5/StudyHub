ALTER TABLE document_chunks ADD COLUMN ingestion_id VARCHAR(36);
ALTER TABLE document_chunks ADD CONSTRAINT fk_chunk_ingestion
    FOREIGN KEY(ingestion_id,document_id) REFERENCES document_ingestions(id,document_id) ON DELETE CASCADE;
CREATE INDEX idx_chunk_ingestion ON document_chunks(ingestion_id);

-- M6-T05 persisted each document atomically. Publish only internally consistent legacy sets.
INSERT INTO document_ingestions(id,document_id,requested_by,status,embedding_model,embedding_dimensions,chunk_count)
SELECT 'legacy-' || d.id,d.id,w.owner_id,'INDEXED',min(ch.embedding_model),min(ch.embedding_dimensions),count(*)
FROM documents d JOIN courses c ON c.id=d.course_id JOIN workspaces w ON w.id=c.workspace_id
JOIN document_chunks ch ON ch.document_id=d.id
WHERE d.managed_file=TRUE
GROUP BY d.id,w.owner_id
HAVING count(DISTINCT ch.embedding_model)=1 AND count(DISTINCT ch.embedding_dimensions)=1;

UPDATE document_chunks ch SET ingestion_id=i.id
FROM document_ingestions i WHERE i.document_id=ch.document_id AND i.status='INDEXED';
UPDATE documents d SET published_ingestion_id=i.id,ingestion_status='INDEXED'
FROM document_ingestions i WHERE i.document_id=d.id AND i.status='INDEXED';

-- Resume uploaded PDFs that did not yet have a complete, compatible index before the upgrade.
INSERT INTO document_ingestions(id,document_id,requested_by,status,active_slot)
SELECT 'pending-' || d.id,d.id,w.owner_id,'PENDING',1
FROM documents d JOIN courses c ON c.id=d.course_id JOIN workspaces w ON w.id=c.workspace_id
WHERE d.managed_file=TRUE AND d.published_ingestion_id IS NULL;
UPDATE documents SET ingestion_status='PENDING' WHERE managed_file=TRUE AND published_ingestion_id IS NULL;
