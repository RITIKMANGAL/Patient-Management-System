package com.patientmanagement;

import com.patientmanagement.ai.service.AiClinicalService;
import com.patientmanagement.appointment.service.AppointmentService;
import com.patientmanagement.auth.repository.AuthUserRepository;
import com.patientmanagement.auth.repository.RefreshTokenRepository;
import com.patientmanagement.auth.repository.RoleRepository;
import com.patientmanagement.communication.service.CommunicationService;
import com.patientmanagement.consultation.service.ConsultationService;
import com.patientmanagement.doctor.service.DoctorService;
import com.patientmanagement.feedback.service.FeedbackService;
import com.patientmanagement.medicalrecord.service.MedicalRecordService;
import com.patientmanagement.patient.service.PatientService;
import com.patientmanagement.prescription.access.service.PrescriptionAccessTokenService;
import com.patientmanagement.prescription.service.PrescriptionPdfService;
import com.patientmanagement.prescription.service.PrescriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PatientManagementApplicationTests {

    @MockitoBean
    private PatientService patientService;

    @MockitoBean
    private DoctorService doctorService;

    @MockitoBean
    private MedicalRecordService medicalRecordService;

    @MockitoBean
    private PrescriptionService prescriptionService;

    @MockitoBean
    private PrescriptionPdfService prescriptionPdfService;

    @MockitoBean
    private PrescriptionAccessTokenService prescriptionAccessTokenService;

    @MockitoBean
    private AppointmentService appointmentService;

    @MockitoBean
    private CommunicationService communicationService;

    @MockitoBean
    private ConsultationService consultationService;

    @MockitoBean
    private FeedbackService feedbackService;

    @MockitoBean
    private AiClinicalService aiClinicalService;

    @MockitoBean
    private AuthUserRepository authUserRepository;

    @MockitoBean
    private com.patientmanagement.doctor.repository.DoctorRepository doctorRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void contextLoads() {
    }
}
