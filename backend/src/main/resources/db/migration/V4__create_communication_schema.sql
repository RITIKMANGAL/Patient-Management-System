CREATE TABLE communications (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    appointment_id UUID,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_message_id VARCHAR(255),
    failure_reason VARCHAR(1000),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_communications_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_communications_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT ck_communications_type CHECK (
        type IN ('APPOINTMENT_CONFIRMATION', 'APPOINTMENT_REMINDER', 'CONSULTATION_COMPLETED', 'PRESCRIPTION_AVAILABLE', 'FEEDBACK_REQUEST')
    ),
    CONSTRAINT ck_communications_channel CHECK (channel IN ('SMS')),
    CONSTRAINT ck_communications_status CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED')),
    CONSTRAINT ck_communications_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_communications_patient_id ON communications (patient_id);
CREATE INDEX idx_communications_appointment_id ON communications (appointment_id);
CREATE INDEX idx_communications_status ON communications (status);
CREATE INDEX idx_communications_type ON communications (type);
CREATE INDEX idx_communications_created_at ON communications (created_at);
