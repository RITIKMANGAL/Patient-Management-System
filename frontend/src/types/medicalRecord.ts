export interface MedicalRecord {
  id: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  diagnosis: string;
  symptoms: string | null;
  notes: string | null;
  recordDate: string;
  createdAt: string;
  updatedAt: string;
}

export interface MedicalRecordRequest {
  patientId: string;
  doctorId: string;
  diagnosis: string;
  symptoms: string | null;
  notes: string | null;
  recordDate: string;
}
