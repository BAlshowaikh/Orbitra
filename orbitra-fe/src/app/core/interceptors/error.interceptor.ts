/*
  error.interceptor.ts
  Surfaces every non-2xx response's backend message via NotificationService,
  and forces logout + redirect to /login on a 401 (invalid/expired JWT - the
  only thing 401 ever means here, confirmed against auth-service's own
  exception handling: bad login credentials return 400, not 401).
  Re-throws afterward so components can still add their own handling on top.
*/

import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ErrorResponse } from '../models/error.model';
import { NotificationService } from '../notifications/notification.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const notificationService = inject(NotificationService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Show the backend's error message in a snackbar, or a generic fallback if none was provided.

      // The HttpErrorResponse (the whole object Angular hands to catchError) will have the below fields:
            // {
            //   status: 400,                 // HTTP status code
            //   statusText: 'Bad Request',
            //   url: 'http://localhost:8080/auth/login',
            //   message: 'Http failure response for http://localhost:8080/auth/login: 400 Bad Request', // Angular's own generic description
            //   error: { "timestamp": ..., "status": ..., "message": ... }                // the actual response BODY the backend sent - this is the important one
            // }

      // IMPORTANT NOTE: Angular parses and attaches whatever the backend sends on any non-2xx, with zero config from us
      // error.error is the actual response body the backend sent
        // { "timestamp": "2026-08-10T09:12:00Z", "status": 400, "message": "Invalid email or password" }
      const body = error.error as ErrorResponse | undefined;
      const message = body?.message ?? 'Something went wrong. Please try again.';
      notificationService.error(message); // this will send the BE message to the snackbar 

      if (error.status === 401) {
        authService.logout();
        router.navigate(['/login']);
      }

      // I already did my global cleanup — now here's the error again, in case you (the component) want to react to it too.
      return throwError(() => error);
    }),
  );
};
