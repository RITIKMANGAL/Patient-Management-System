ALTER TABLE doctors
    ADD COLUMN user_id UUID;

ALTER TABLE doctors
    ADD CONSTRAINT fk_doctors_user
    FOREIGN KEY (user_id)
    REFERENCES users (id)
    ON DELETE SET NULL;

ALTER TABLE doctors
    ADD CONSTRAINT uk_doctors_user
    UNIQUE (user_id);
