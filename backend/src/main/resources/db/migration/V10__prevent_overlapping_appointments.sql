CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointments ADD CONSTRAINT ex_appointments_doctor_slot
    EXCLUDE USING gist (
        doctor_id WITH =,
        tsrange(appointment_date_time, appointment_date_time + interval '30 minutes', '[)') WITH &&
    ) WHERE (status IN ('SCHEDULED', 'CONFIRMED'));

ALTER TABLE appointments ADD CONSTRAINT ex_appointments_patient_slot
    EXCLUDE USING gist (
        patient_id WITH =,
        tsrange(appointment_date_time, appointment_date_time + interval '30 minutes', '[)') WITH &&
    ) WHERE (status IN ('SCHEDULED', 'CONFIRMED'));
