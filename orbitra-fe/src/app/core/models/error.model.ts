/*
  error.model.ts
  The {timestamp, status, message} shape every backend service (and the
  Gateway itself) returns on a non-2xx response.
*/

export interface ErrorResponse {
  timestamp: string;
  status: number;
  message: string;
}
