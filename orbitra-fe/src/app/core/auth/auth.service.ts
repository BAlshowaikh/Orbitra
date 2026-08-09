/*
  auth.service.ts
  Holds the current JWT as a signal, persisted to localStorage, and derives
  the logged-in user's identity (accountId/role/partnerType) from its claims.
*/

import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { jwtDecode } from 'jwt-decode';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest } from '../models/auth.model';
import { PartnerType, Role } from '../models/role.model';

const TOKEN_STORAGE_KEY = 'orbitra_token';

// The claims auth-service embeds in every JWT (sub/role/partnerType), plus
// the standard exp claim every JWT carries for expiry.
interface JwtClaims {
  sub: string; // Account.id - always a string in a JWT, even though it's numeric on the backend
  role: Role;
  partnerType: PartnerType;
  exp: number; // expiry, in seconds since epoch
}

export interface CurrentUser {
  accountId: number;
  role: Role;
  partnerType: PartnerType;
}

// This means create exactly one instance of this, and hand that same instance to anything that asks for it, anywhere in the app
@Injectable({ providedIn: 'root' })
export class AuthService {
  constructor(private readonly http: HttpClient) {}
  // ------------ STATE: the one raw signal everything else derives from ------------
  // Private and mutable - only this service's own methods may change it.
  // Seeded from localStorage so a page refresh doesn't lose the session.
  private readonly tokenSignal = signal<string | null>(localStorage.getItem(TOKEN_STORAGE_KEY));

  // ------------ PROPERTY 1: decode the current user's identity from the JWT ------------
  readonly currentUser = computed<CurrentUser | null>(() => {
    const token = this.tokenSignal();
    if (!token) {
      return null;
    }
    const claims = jwtDecode<JwtClaims>(token);
    return {
      accountId: Number(claims.sub),
      role: claims.role,
      partnerType: claims.partnerType,
    };
  });

  // ------------ PROPERTY 2: check the JWT's own exp claim against the current time ------------
  readonly isExpired = computed<boolean>(() => {
    const token = this.tokenSignal();
    if (!token) {
      return true;
    }
    const claims = jwtDecode<JwtClaims>(token);
    return Date.now() >= claims.exp * 1000; // exp is seconds, Date.now() is milliseconds
  });

  // ------------ PROPERTY 3: convenience checks built on top of properties 1 and 2 ------------
  readonly isAuthenticated = computed<boolean>(() => this.currentUser() !== null && !this.isExpired());
  readonly isTraveler = computed<boolean>(() => this.currentUser()?.role === 'TRAVELER');
  readonly isPartner = computed<boolean>(() => this.currentUser()?.role === 'PARTNER');
  readonly isAdmin = computed<boolean>(() => this.currentUser()?.role === 'ADMIN');


  // ------------ METHOD 1: read the raw token (e.g. for the auth interceptor) ------------
  get token(): string | null {
    return this.tokenSignal();
  }

  // ------------ METHOD 2: store a new token after a successful login/register ------------
  setToken(token: string): void {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
    this.tokenSignal.set(token);
  }

  // ------------ METHOD 3: clear the session on logout (or a failed token refresh) ------------
  clearToken(): void {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    this.tokenSignal.set(null);
  }

  // ------------ METHOD 4: log in an existing account ------------
  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, credentials)
      .pipe(tap((response) => this.setToken(response.token)));
  }

  // ------------ METHOD 5: register a new account ------------
  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/register`, request)
      .pipe(tap((response) => this.setToken(response.token)));
  }

  // ------------ METHOD 6: log out - no backend call needed, sessions aren't tracked server-side ------------
  logout(): void {
    this.clearToken();
  }
}
