ALTER TABLE questions ADD COLUMN concept_id BIGINT;

ALTER TABLE questions ADD CONSTRAINT fk_questions_concept FOREIGN KEY (concept_id) REFERENCES concepts(id) ON DELETE SET NULL;

CREATE INDEX idx_questions_concept_id ON questions(concept_id);