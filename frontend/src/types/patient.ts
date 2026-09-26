export type PatientGender = "FEMALE" | "MALE" | "OTHER" | "UNKNOWN";

export type BloodGroup =
  | "A_POSITIVE"
  | "A_NEGATIVE"
  | "B_POSITIVE"
  | "B_NEGATIVE"
  | "AB_POSITIVE"
  | "AB_NEGATIVE"
  | "O_POSITIVE"
  | "O_NEGATIVE";

export interface Patient {
  id: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: PatientGender;
  bloodGroup: BloodGroup | null;
  phone: string;
  email: string | null;
  address: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PatientRequest {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: PatientGender;
  bloodGroup: BloodGroup | null;
  phone: string;
  email: string | null;
  address: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
}
