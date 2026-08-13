/*
  auth.routes.ts
  Lazy-loaded routes for the auth feature. loadComponent means a page's code
  only downloads when a visitor actually navigates there, not as part of the
  app's initial bundle.
*/

import { Routes } from '@angular/router';

export const authRoutes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login-page/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    loadComponent: () => import('./pages/register-page/register-page').then((m) => m.RegisterPage),
  },
];
