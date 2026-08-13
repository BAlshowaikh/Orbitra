/*
  notification.service.ts
  Thin wrapper around Angular Material's MatSnackBar - one place to change
  toast styling/duration later without touching every call site.
*/

import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(private readonly snackBar: MatSnackBar) {}

  // ------------ METHOD 1: show a backend/API error message ------------
  error(message: string): void {
    this.snackBar.open(message, 'Dismiss', { duration: 6000 });
  }
}
