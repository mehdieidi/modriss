CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

DO $$
BEGIN
    IF to_regtype('public.vector') IS NOT NULL THEN
        ALTER TABLE assistant_retrieval_documents
            ALTER COLUMN embedding TYPE public.vector(384)
            USING embedding::public.vector(384);
    END IF;
END $$;
