CREATE TABLE IF NOT EXISTS qa_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL DEFAULT '新对话',
    last_message_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_qa_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_qa_sessions_user_id ON qa_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_qa_sessions_last_message_at ON qa_sessions(last_message_at DESC);

CREATE TABLE IF NOT EXISTS qa_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    rewritten_query TEXT,
    sources_json JSONB,
    confidence DOUBLE PRECISION,
    prompt_hash VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    error_code VARCHAR(128),
    processing_time_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_qa_messages_session FOREIGN KEY (session_id) REFERENCES qa_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_qa_messages_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_qa_messages_session_id ON qa_messages(session_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_qa_messages_user_id ON qa_messages(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chunks_text_fts
    ON chunks
    USING GIN (to_tsvector('simple', COALESCE(text, '')));

CREATE INDEX IF NOT EXISTS idx_documents_markdown_fts
    ON documents
    USING GIN (to_tsvector('simple', COALESCE(content_markdown, '')));
