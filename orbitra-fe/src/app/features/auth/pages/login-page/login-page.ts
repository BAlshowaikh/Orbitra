/*
  login-page.ts
  The login screen - Reactive Form (email/password), submits via
  AuthService.login(), navigates home on success. Errors are already
  surfaced globally by errorInterceptor's toast; isSubmitting here just
  resets the button/loading state on both success and failure.
*/

import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { AuthLayout } from '../../components/auth-layout/auth-layout';
import { AuthService } from '../../../../core/auth/auth.service';

@Component({
  selector: 'app-login-page',
  imports: [AuthLayout, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, RouterLink],
  templateUrl: './login-page.html',
})
export class LoginPage {
  // ------------ DEPENDENCIES ------------
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  // ------------ FORM STATE ------------
  // Reactive Form: one FormControl per field, validated declaratively rather
  // than by hand in the template.
  protected readonly loginForm = new FormGroup({
    email: new FormControl('', [Validators.required, Validators.email]),
    password: new FormControl('', Validators.required),
  });

  // Drives the submit button's disabled/loading state - reset on both
  // success and failure, since the request always ends one way or the other.
  protected isSubmitting = false;

  // ------------ METHOD 1: submit the login form ------------
  protected onSubmit(): void {
    if (this.loginForm.invalid) {
      return;
    }

    this.isSubmitting = true;
    const { email, password } = this.loginForm.value;

    // Call AuthService.login() with the form values, subscribe to the result,
    this.authService.login({ email: email!, password: password! }).subscribe({
      next: () => this.router.navigate(['/']), // if successful, navigate home
      error: () => {
        this.isSubmitting = false;
      },
    });
  }
}
