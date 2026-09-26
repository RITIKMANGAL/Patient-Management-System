package com.patientmanagement.prescription.access.service;

public class PrescriptionAccessDeniedException extends RuntimeException {

    public PrescriptionAccessDeniedException() {
        super("Prescription access link is invalid or expired");
    }
}
