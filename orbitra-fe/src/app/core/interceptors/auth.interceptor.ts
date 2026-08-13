/*
  auth.interceptor.ts
  Attaches the current JWT (if any) as a Bearer token to every outgoing
  HTTP request. Safe to apply unconditionally - the Gateway itself decides
  which routes are public, ignoring the header there either way.
*/

import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // 1. Grap the AuthService instance from the DI container, 
  // 2 read its current token signal by calling the token (the getter) method
  const token = inject(AuthService).token;

  if (!token) {
    return next(req);
  }

  // 1. Clone the request 
  // 2. Set the new header
  // 3. Pass the cloned request to next()
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
