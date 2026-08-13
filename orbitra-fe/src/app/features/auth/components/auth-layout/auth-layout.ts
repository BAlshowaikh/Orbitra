/*
  auth-layout.ts
  Shared visual shell for the Login/Register pages - full-bleed night-sky
  photo, branding copy, and a glass card slot filled via content projection
  (<ng-content>) so each page supplies its own form inside the same shell.
*/

import { Component } from '@angular/core';

@Component({
  selector: 'app-auth-layout',
  imports: [],
  templateUrl: './auth-layout.html',
  styleUrl: './auth-layout.css',
})
export class AuthLayout {}
