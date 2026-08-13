/*
  app.routes.ts
  Root route table. Each feature (auth, hotels, flights, bookings, ...) owns
  its own <feature>.routes.ts file (see features/auth/auth.routes.ts) - this
  file just wires each of those in via loadChildren, one entry per feature,
  rather than listing every individual page route directly here.

  loadChildren: like loadComponent (see auth.routes.ts), but for a whole
  routes array instead of a single component - it lazy-loads an entire
  feature's routes file only once a visitor navigates somewhere inside it,
  and everything that feature file exports gets nested under this entry's
  own `path` as a prefix (see the comment on 'auth' below for what that
  means in practice).
*/

import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    // path: '' adds no prefix - authRoutes' own paths (e.g. 'login') become
    // the whole URL (/login), not /auth/login. Used purely to keep the auth
    // feature's routes organized in their own file without that file
    // structure leaking into the actual URLs.
    path: '',
    loadChildren: () => import('./features/auth/auth.routes').then((m) => m.authRoutes),
  },
];
