DO $$
BEGIN
    IF to_regtype('public.vector') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_assistant_retrieval_embedding_hnsw
            ON assistant_retrieval_documents
            USING hnsw (embedding public.vector_cosine_ops);
    END IF;
END $$;
