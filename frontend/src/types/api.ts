export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  timestamp: string;
  path?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
