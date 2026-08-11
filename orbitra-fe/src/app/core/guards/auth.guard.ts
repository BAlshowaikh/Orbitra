/*
  auth.guard.ts
  Blocks a route unless the user is logged in (and their token hasn't
  expired). UX convenience only - real authorization is already fully
  enforced server-side.
*/

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};
