CREATE TABLE feedback (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    doctor_id UUID NOT NULL,
    rating INTEGER NOT NULL,
    comment VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_feedback_consultation UNIQUE (consultation_id),
    CONSTRAINT fk_feedback_consultation FOREIGN KEY (consultation_id) REFERENCES consultations (id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_feedback_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT ck_feedback_rating CHECK (rating >= 1 AND rating <= 5)
);

CREATE TABLE feedback_access_tokens (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_feedback_access_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_feedback_access_tokens_consultation
        FOREIGN KEY (consultation_id) REFERENCES consultations (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_feedback_access_tokens_active_consultation
    ON feedback_access_tokens (consultation_id)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_feedback_consultation_id ON feedback (consultation_id);
CREATE INDEX idx_feedback_patient_id ON feedback (patient_id);
CREATE INDEX idx_feedback_doctor_id ON feedback (doctor_id);
CREATE INDEX idx_feedback_rating ON feedback (rating);
CREATE INDEX idx_feedback_created_at ON feedback (created_at);
CREATE INDEX idx_feedback_access_tokens_consultation_id ON feedback_access_tokens (consultation_id);
CREATE INDEX idx_feedback_access_tokens_token_hash ON feedback_access_tokens (token_hash);
CREATE INDEX idx_feedback_access_tokens_expires_at ON feedback_access_tokens (expires_at);
CREATE INDEX idx_feedback_access_tokens_revoked_at ON feedback_access_tokens (revoked_at);
