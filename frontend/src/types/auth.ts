export type RoleName = "ADMIN" | "DOCTOR" | "RECEPTIONIST";

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  firstName: string;
  lastName: string;
  role: RoleName;
  doctorId?: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
}

export interface UserResponse {
  id: string;
  username: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  roles: RoleName[];
  createdAt: string;
  updatedAt: string;
}

export interface AuthUser {
  id: string;
  username: string;
  roles: RoleName[];
  expiresAt: number;
}
