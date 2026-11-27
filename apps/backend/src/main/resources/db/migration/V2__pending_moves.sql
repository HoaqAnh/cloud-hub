CREATE TABLE pending_moves (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_account_id UUID NOT NULL REFERENCES cloud_accounts(id) ON DELETE CASCADE,
    source_file_id TEXT NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE
);
