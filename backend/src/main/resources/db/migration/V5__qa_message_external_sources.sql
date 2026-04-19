ALTER TABLE qa_messages
    ADD COLUMN IF NOT EXISTS external_sources_json JSONB;
