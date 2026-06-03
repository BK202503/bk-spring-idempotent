CREATE TABLE IF NOT EXISTS idempotency_keys (
    key_id          VARCHAR(255) NOT NULL PRIMARY KEY,
    body_hash       VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    response_status INT          NULL,
    response_headers CLOB        NULL,
    response_body   BLOB         NULL,
    reserved_at     TIMESTAMP    NOT NULL,
    completed_at    TIMESTAMP    NULL,
    expires_at      TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);
