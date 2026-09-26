package com.patientmanagement.prescription.service;

import com.patientmanagement.auth.security.ClinicalAccessService;
import com.patientmanagement.common.exception.ResourceNotFoundException;
import com.patientmanagement.doctor.model.Doctor;
import com.patientmanagement.patient.model.Patient;
import com.patientmanagement.prescription.model.Prescription;
import com.patientmanagement.prescription.model.PrescriptionItem;
import com.patientmanagement.prescription.repository.PrescriptionRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescriptionPdfService {

    private static final float MARGIN = 54;
    private static final float FONT_SIZE = 10;
    private static final float LINE_HEIGHT = 15;
    private static final float SECTION_GAP = 18;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);
    private static final PDFont REGULAR_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final PrescriptionRepository prescriptionRepository;
    private final ClinicalAccessService clinicalAccessService;

    @Autowired
    public PrescriptionPdfService(PrescriptionRepository prescriptionRepository, ClinicalAccessService clinicalAccessService) {
        this.prescriptionRepository = prescriptionRepository;
        this.clinicalAccessService = clinicalAccessService;
    }

    public PrescriptionPdfService(PrescriptionRepository prescriptionRepository) {
        this.prescriptionRepository = prescriptionRepository;
        this.clinicalAccessService = null;
    }

    @Transactional(readOnly = true)
    public byte[] generatePrescriptionPdf(UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found"));
        if (clinicalAccessService != null) {
            clinicalAccessService.requirePrescriptionAccess(prescription);
        }

        try (PDDocument document = new PDDocument()) {
            PdfWriter writer = new PdfWriter(document);
            writer.brand("Clinora", "Modern Clinic Management Platform");
            writer.title("Prescription");
            writer.keyValue("Prescription date", DATE_FORMATTER.format(prescription.getPrescriptionDate()));
            writer.section("Patient");
            writer.keyValue("Name", fullName(prescription.getPatient()));
            writer.keyValue("Date of birth", DATE_FORMATTER.format(prescription.getPatient().getDateOfBirth()));
            writer.keyValue("Gender", displayEnum(prescription.getPatient().getGender().name()));
            writer.keyValue("Blood group", prescription.getPatient().getBloodGroup() == null
                    ? "-"
                    : displayEnum(prescription.getPatient().getBloodGroup().name()));
            writer.keyValue("Phone", prescription.getPatient().getPhone());
            writer.keyValue("Email", valueOrDash(prescription.getPatient().getEmail()));

            writer.section("Doctor");
            writer.keyValue("Name", fullName(prescription.getDoctor()));
            writer.keyValue("Specialization", prescription.getDoctor().getSpecialization());
            writer.keyValue("Department", valueOrDash(prescription.getDoctor().getDepartment()));
            writer.keyValue("License number", prescription.getDoctor().getLicenseNumber());
            writer.keyValue("Phone", prescription.getDoctor().getPhone());
            writer.keyValue("Email", valueOrDash(prescription.getDoctor().getEmail()));

            writer.section("Medicines");
            int itemNumber = 1;
            for (PrescriptionItem item : prescription.getItems()) {
                writer.boldLine(itemNumber + ". " + item.getMedicineName());
                writer.keyValue("Dosage", item.getDosage());
                writer.keyValue("Frequency", item.getFrequency());
                writer.keyValue("Duration", item.getDuration());
                writer.keyValue("Instructions", valueOrDash(item.getInstructions()));
                writer.gap(6);
                itemNumber++;
            }

            String notes = displayableNotes(prescription.getNotes());
            if (notes != null) {
                writer.section("Notes");
                writer.paragraph(notes);
            }

            writer.footer("Clinora - generated for clinical use.");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException | RuntimeException exception) {
            throw new PrescriptionPdfGenerationException("Unable to generate prescription PDF", exception);
        }
    }

    public String filenameFor(UUID prescriptionId) {
        return "prescription-" + prescriptionId + ".pdf";
    }

    private String fullName(Patient patient) {
        return patient.getFirstName() + " " + patient.getLastName();
    }

    private String fullName(Doctor doctor) {
        return doctor.getFirstName() + " " + doctor.getLastName();
    }

    private static String valueOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    private static String displayEnum(String value) {
        String[] parts = value.toLowerCase(Locale.ENGLISH).split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private static String displayableNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String visible = notes.replaceFirst("^\\s*\\[DEMO:[^]]+]\\s*", "").trim();
        if (visible.toLowerCase(Locale.ENGLISH).contains("demo-only")
                || visible.toLowerCase(Locale.ENGLISH).contains("development-only")
                || visible.toLowerCase(Locale.ENGLISH).contains("fictional clinical note")) {
            return null;
        }
        return visible.isBlank() ? null : visible;
    }

    private static class PdfWriter {

        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        PdfWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        void brand(String name, String descriptor) throws IOException {
            writeLine(name, BOLD_FONT, 20);
            writeLine(descriptor, REGULAR_FONT, 10);
            gap(SECTION_GAP);
        }

        void title(String value) throws IOException {
            writeLine(value, BOLD_FONT, 18);
            gap(SECTION_GAP);
        }

        void section(String heading) throws IOException {
            gap(SECTION_GAP / 2);
            writeLine(heading, BOLD_FONT, 13);
            gap(5);
        }

        void keyValue(String key, String value) throws IOException {
            paragraph(key + ": " + valueOrDash(value));
        }

        void boldLine(String value) throws IOException {
            writeLine(value, BOLD_FONT, FONT_SIZE);
        }

        void paragraph(String value) throws IOException {
            for (String line : wrap(valueOrDash(value), REGULAR_FONT, FONT_SIZE, writableWidth())) {
                writeLine(line, REGULAR_FONT, FONT_SIZE);
            }
        }

        void gap(float amount) throws IOException {
            ensureSpace(amount);
            y -= amount;
        }

        void footer(String value) throws IOException {
            ensureSpace(SECTION_GAP + LINE_HEIGHT);
            y -= SECTION_GAP;
            writeLine(value, REGULAR_FONT, 9);
            closeStream();
        }

        private void writeLine(String value, PDFont font, float fontSize) throws IOException {
            ensureSpace(LINE_HEIGHT);
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(pdfSafe(value));
            stream.endText();
            y -= LINE_HEIGHT;
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (y - requiredHeight < MARGIN) {
                closeStream();
                newPage();
            }
        }

        private void newPage() throws IOException {
            page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void closeStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private float writableWidth() {
            return page.getMediaBox().getWidth() - (MARGIN * 2);
        }

        private static List<String> wrap(String value, PDFont font, float fontSize, float maxWidth) throws IOException {
            String sanitized = pdfSafe(value);
            String[] words = sanitized.split("\\s+");
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();

            for (String word : words) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (textWidth(candidate, font, fontSize) <= maxWidth) {
                    current = new StringBuilder(candidate);
                } else {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                    }
                    current = new StringBuilder(word);
                }
            }

            if (!current.isEmpty()) {
                lines.add(current.toString());
            }

            return lines.isEmpty() ? List.of("-") : lines;
        }

        private static float textWidth(String value, PDFont font, float fontSize) throws IOException {
            return font.getStringWidth(value) / 1000 * fontSize;
        }

        private static String pdfSafe(String value) {
            return valueOrDash(value)
                    .replaceAll("[\\r\\n\\t]+", " ")
                    .replaceAll("[^\\x20-\\x7E]", "?")
                    .trim();
        }
    }
}
