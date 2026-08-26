package com.patientmanagement;

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

    @Test
    void contextLoads() {
    }
}
