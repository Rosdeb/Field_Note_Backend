CREATE TABLE IF NOT EXISTS revoked_tokens (
 token_hash CHAR(64) PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS revoked_tokens_user_idx ON revoked_tokens(user_id);
