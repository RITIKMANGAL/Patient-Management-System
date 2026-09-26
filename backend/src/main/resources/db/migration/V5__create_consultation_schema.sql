CREATE TABLE consultations (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    chief_complaint VARCHAR(4000),
    symptoms VARCHAR(4000),
    examination VARCHAR(4000),
    assessment VARCHAR(4000),
    treatment VARCHAR(4000),
    follow_up_instructions VARCHAR(4000),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_consultations_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT uk_consultations_appointment UNIQUE (appointment_id),
    CONSTRAINT ck_consultations_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_consultations_completed_at CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    )
);

CREATE INDEX idx_consultations_status ON consultations (status);
CREATE INDEX idx_consultations_started_at ON consultations (started_at);
CREATE INDEX idx_consultations_completed_at ON consultations (completed_at);
