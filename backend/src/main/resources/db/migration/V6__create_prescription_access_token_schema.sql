CREATE TABLE prescription_access_tokens (
    id UUID PRIMARY KEY,
    prescription_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_prescription_access_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_prescription_access_tokens_prescription
        FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_prescription_access_tokens_active_prescription
    ON prescription_access_tokens (prescription_id)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_prescription_access_tokens_prescription_id ON prescription_access_tokens (prescription_id);
CREATE INDEX idx_prescription_access_tokens_token_hash ON prescription_access_tokens (token_hash);
CREATE INDEX idx_prescription_access_tokens_expires_at ON prescription_access_tokens (expires_at);
CREATE INDEX idx_prescription_access_tokens_revoked_at ON prescription_access_tokens (revoked_at);
