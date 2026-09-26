export interface Doctor {
  id: string;
  firstName: string;
  lastName: string;
  specialization: string;
  licenseNumber: string;
  phone: string;
  email: string | null;
  department: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DoctorRequest {
  firstName: string;
  lastName: string;
  specialization: string;
  licenseNumber: string;
  phone: string;
  email: string | null;
  department: string | null;
}
