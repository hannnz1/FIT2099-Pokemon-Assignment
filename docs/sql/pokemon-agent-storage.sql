-- Dedicated PostgreSQL database only. No extension, service or unrelated table changes.
CREATE TABLE IF NOT EXISTS pokemon_agent_world_checkpoints (
    owner_hash VARCHAR(64) PRIMARY KEY,
    document TEXT NOT NULL CHECK (octet_length(document) <= 1048576),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
