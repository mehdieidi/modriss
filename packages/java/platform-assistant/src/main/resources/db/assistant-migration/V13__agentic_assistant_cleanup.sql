-- The agentic tool loop derives contracts directly from Ecore and no longer persists retrieval
-- vectors, model-context search indexes, or structured clarification state.
DROP TABLE IF EXISTS assistant_pending_interactions;
DROP TABLE IF EXISTS assistant_model_contexts;
DROP TABLE IF EXISTS assistant_retrieval_documents;
DROP TABLE IF EXISTS assistant_source_evidence;
DROP TABLE IF EXISTS assistant_metamodel_contracts;
DROP TABLE IF EXISTS assistant_turn_diagnostics;
DROP TABLE IF EXISTS assistant_turn_executions;
