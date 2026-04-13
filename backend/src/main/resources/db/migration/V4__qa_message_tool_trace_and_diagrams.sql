ALTER TABLE qa_messages
    ADD COLUMN IF NOT EXISTS plan_summary VARCHAR(512),
    ADD COLUMN IF NOT EXISTS tool_trace_json JSONB,
    ADD COLUMN IF NOT EXISTS diagrams_json JSONB;
