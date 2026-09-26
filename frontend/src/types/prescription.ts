export interface PrescriptionItem {
  id: string;
  medicineName: string;
  dosage: string;
  frequency: string;
  duration: string;
  instructions: string | null;
}

export interface Prescription {
  id: string;
  patientId: string;
  patientName: string;
  doctorId: string;
  doctorName: string;
  prescriptionDate: string;
  notes: string | null;
  items: PrescriptionItem[];
  createdAt: string;
  updatedAt: string;
}

export interface PrescriptionItemRequest {
  medicineName: string;
  dosage: string;
  frequency: string;
  duration: string;
  instructions: string | null;
}

export interface PrescriptionRequest {
  patientId: string;
  doctorId: string;
  prescriptionDate: string;
  notes: string | null;
  items: PrescriptionItemRequest[];
}

export interface PrescriptionAccessTokenResponse {
  token: string;
  expiresAt: string;
}

export interface PrescriptionAccessStatusResponse {
  active: boolean;
  expiresAt: string | null;
}
