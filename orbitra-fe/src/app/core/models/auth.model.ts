/*
  auth.model.ts
  TypeScript interfaces for auth-service's request/response DTOs
  (RegisterRequest, LoginRequest, AuthResponse, MeResponse).
*/

import { PartnerType, Role } from './role.model';

export interface RegisterRequest {
  email: string;
  password: string;
  role: Role;
  partnerType?: PartnerType; // required if role is 'PARTNER', omitted/null otherwise
}

export interface LoginRequest {
  email: string;
  password: string;
}

// Returned by both POST /auth/register and POST /auth/login
export interface AuthResponse {
  token: string;
  expiresInMs: number;
  role: Role;
}

// Returned by GET /auth/me - identity read straight from the JWT, no DB lookup
export interface MeResponse {
  accountId: number;
  role: Role;
}
