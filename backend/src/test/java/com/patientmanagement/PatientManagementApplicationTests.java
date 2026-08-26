package com.patientmanagement;

import com.patientmanagement.auth.repository.AuthUserRepository;
import com.patientmanagement.auth.repository.RefreshTokenRepository;
import com.patientmanagement.auth.repository.RoleRepository;
import com.patientmanagement.doctor.service.DoctorService;
import com.patientmanagement.medicalrecord.service.MedicalRecordService;
import com.patientmanagement.patient.service.PatientService;
import com.patientmanagement.prescription.service.PrescriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PatientManagementApplicationTests {

    @MockBean
    private PatientService patientService;

    @MockBean
    private DoctorService doctorService;

    @MockBean
    private MedicalRecordService medicalRecordService;

    @MockBean
    private PrescriptionService prescriptionService;

    @MockBean
    private AuthUserRepository authUserRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void contextLoads() {
    }
}
