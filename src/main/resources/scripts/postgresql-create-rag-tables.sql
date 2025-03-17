CREATE TABLE IF NOT EXISTS rag_processed_vector_documents (
processed_document_id uuid NOT NULL PRIMARY KEY,
source_path TEXT NOT NULL,
hash TEXT NOT NULL,
first_processed_at timestamptz NOT NULL,
last_processed_at timestamptz NOT NULL
);

ALTER TABLE rag_processed_vector_documents
DROP CONSTRAINT IF EXISTS rag_processed_vector_documents_source_path_key;

CREATE UNIQUE INDEX IF NOT EXISTS rag_processed_vector_documents_source_path_idx ON
public.rag_processed_vector_documents (LOWER(source_path));

CREATE TABLE IF NOT EXISTS rag_processed_vector_document_chunks (
chunk_id TEXT NOT NULL PRIMARY KEY,
processed_document_id UUID NOT NULL,
FOREIGN KEY (processed_document_id) REFERENCES rag_processed_vector_documents(processed_document_id) ON
DELETE
	CASCADE
);
