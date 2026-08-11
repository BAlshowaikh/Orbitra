import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';

// ------------ NOTES:
// 1. provideHttpClient -- How the request pipeline is configured. 
// what happens when AuthService.login() runs this.http.post(...):
  // 1. That call doesn't go straight to the network. Angular first hands the request to authInterceptor.
  // 2. authInterceptor attaches the token, then calls next(req) — which, because of the order we registered them in, hands the request to errorInterceptor next.
  // 3. errorInterceptor calls next(req) too — and this next is what actually triggers the real network request (there's nothing else left in the chain after it).
  // 4. If the backend responds with an error, that error flows back up through the exact same chain in reverse — it arrives at errorInterceptor's next(req) first, which is what .pipe(catchError(...)) is watching.
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor]))
  ]
};
