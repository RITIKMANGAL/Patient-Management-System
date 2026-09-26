package com.patientmanagement.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.patient.model.BloodGroup;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.patient.model.PatientGender;
import com.patientmanagement.prescription.model.Prescription;
import com.patientmanagement.prescription.model.PrescriptionItem;
import com.patientmanagement.prescription.repository.PrescriptionRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PrescriptionPdfServiceTests {

    @Mock
    private PrescriptionRepository prescriptionRepository;

    private PrescriptionPdfService prescriptionPdfService;

    @BeforeEach
    void setUp() {
        prescriptionPdfService = new PrescriptionPdfService(prescriptionRepository);
    }

    @Test
    void generatePrescriptionPdfReturnsReadablePdfForValidPrescription() throws IOException {
        UUID prescriptionId = UUID.randomUUID();
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription()));

        byte[] pdf = prescriptionPdfService.generatePrescriptionPdf(prescriptionId);

        assertThat(pdf).isNotEmpty();
        assertThat(pdf).startsWith("%PDF".getBytes());
        assertThat(textFrom(pdf))
                .contains("Clinora")
                .contains("Modern Clinic Management Platform")
                .contains("Prescription")
                .contains("Asha Rao")
                .contains("Kiran Shah")
                .contains("05 Sep 2026")
                .contains("01 Jan 1990")
                .contains("Female")
                .contains("O Positive")
                .contains("Synthetic medicine A")
                .contains("10mg")
                .contains("Once daily")
                .contains("5 days")
                .contains("After food")
                .contains("Use exactly as directed");
    }

    @Test
    void generatePrescriptionPdfDoesNotIncludeInternalIdentifiersOrSecurityData() throws IOException {
        UUID prescriptionId = UUID.randomUUID();
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription()));

        String text = textFrom(prescriptionPdfService.generatePrescriptionPdf(prescriptionId));

        assertThat(text)
                .doesNotContain(prescriptionId.toString())
                .doesNotContain("JWT")
                .doesNotContain("refresh")
                .doesNotContain("password")
                .doesNotContain("AI");
    }

    @Test
    void generatePrescriptionPdfDoesNotIncludeDevelopmentSeedNotes() throws IOException {
        UUID prescriptionId = UUID.randomUUID();
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(
                prescription("[DEMO:PRESCRIPTION_AVAILABLE] Development-only prescription note")
        ));

        String text = textFrom(prescriptionPdfService.generatePrescriptionPdf(prescriptionId));

        assertThat(text)
                .doesNotContain("[DEMO:PRESCRIPTION_AVAILABLE]")
                .doesNotContain("Development-only prescription note")
                .doesNotContain("Notes");
    }

    @Test
    void generatePrescriptionPdfThrowsNotFoundWhenPrescriptionDoesNotExist() {
        UUID prescriptionId = UUID.randomUUID();
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prescriptionPdfService.generatePrescriptionPdf(prescriptionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Prescription not found");
    }

    @Test
    void generatePrescriptionPdfWrapsUnexpectedGenerationFailures() {
        UUID prescriptionId = UUID.randomUUID();
        Prescription brokenPrescription = new Prescription(null, doctor(), LocalDate.of(2026, 9, 5), null);
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(brokenPrescription));

        assertThatThrownBy(() -> prescriptionPdfService.generatePrescriptionPdf(prescriptionId))
                .isInstanceOf(PrescriptionPdfGenerationException.class)
                .hasMessage("Unable to generate prescription PDF");
    }

    @Test
    void filenameUsesSafePdfExtension() {
        UUID prescriptionId = UUID.randomUUID();

        assertThat(prescriptionPdfService.filenameFor(prescriptionId))
                .isEqualTo("prescription-" + prescriptionId + ".pdf");
    }

    private String textFrom(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private Prescription prescription() {
        return prescription("Use exactly as directed");
    }

    private Prescription prescription(String notes) {
        Prescription prescription = new Prescription(
                patient(),
                doctor(),
                LocalDate.of(2026, 9, 5),
                notes
        );
        prescription.addItem(new PrescriptionItem(
                "Synthetic medicine A",
                "10mg",
                "Once daily",
                "5 days",
                "After food"
        ));
        ReflectionTestUtils.setField(prescription, "id", UUID.randomUUID());
        return prescription;
    }

    private Patient patient() {
        Patient patient = new Patient(
                "Asha",
                "Rao",
                LocalDate.of(1990, 1, 1),
                PatientGender.FEMALE,
                BloodGroup.O_POSITIVE,
                "+15555550100",
                "asha.rao@example.com",
                "Synthetic address",
                "Synthetic Contact",
                "+15555550101"
        );
        ReflectionTestUtils.setField(patient, "id", UUID.randomUUID());
        return patient;
    }

    private Doctor doctor() {
        Doctor doctor = new Doctor(
                "Kiran",
                "Shah",
                "Cardiology",
                "LIC-100",
                "+15555550200",
                "kiran.shah@example.com",
                "Cardiology"
        );
        ReflectionTestUtils.setField(doctor, "id", UUID.randomUUID());
        return doctor;
    }
}
