ALTER TABLE communications DROP CONSTRAINT ck_communications_status;
ALTER TABLE communications ADD CONSTRAINT ck_communications_status
    CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED', 'SIMULATED', 'DISABLED'));
