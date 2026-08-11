/*
  role.guard.ts
  Guard factories for role/partnerType-restricted routes. Not authenticated
  at all redirects to /login; authenticated with the wrong role/type
  redirects to the friendly error page instead - they're already logged in,
  so sending them back to /login again wouldn't make sense.
*/

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { PartnerType, Role } from '../models/role.model';

const FORBIDDEN_QUERY_PARAMS = { status: 403, message: 'You do not have access to this page.' };

// ------------ GUARD 1: must be logged in as a specific role ------------
export function roleGuard(role: Role): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
      router.navigate(['/login']);
      return false;
    }

    if (authService.currentUser()?.role === role) {
      return true;
    }

    router.navigate(['/error'], { queryParams: FORBIDDEN_QUERY_PARAMS });
    return false;
  };
}

// ------------ GUARD 2: must be a PARTNER of a specific partnerType ------------
export function partnerTypeGuard(partnerType: PartnerType): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
      router.navigate(['/login']);
      return false;
    }

    const user = authService.currentUser();
    if (user?.role === 'PARTNER' && user.partnerType === partnerType) {
      return true;
    }

    router.navigate(['/error'], { queryParams: FORBIDDEN_QUERY_PARAMS });
    return false;
  };
}
