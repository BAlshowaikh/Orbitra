/*
  register-page.ts
  The register screen - Reactive Form (email/password/role, conditional
  partnerType), submits via AuthService.register(), navigates home on
  success. No Admin option here - bootstrap admin creation stays a manual,
  one-time backend action, not part of this form (see
  project_auth_service_admin_registration memory).
*/

import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { AuthLayout } from '../../components/auth-layout/auth-layout';
import { AuthService } from '../../../../core/auth/auth.service';
import { PartnerType, Role } from '../../../../core/models/role.model';

@Component({
  selector: 'app-register-page',
  imports: [
    AuthLayout,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonToggleModule,
    MatButtonModule,
    RouterLink,
  ],
  templateUrl: './register-page.html',
})
export class RegisterPage {
  // ------------ DEPENDENCIES ------------
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  // ------------ FORM STATE ------------
  // partnerType starts with no validators - role.valueChanges (below) adds
  // Validators.required only while role is 'PARTNER'.
  protected readonly registerForm = new FormGroup({
    email: new FormControl('', [Validators.required, Validators.email]),
    password: new FormControl('', [Validators.required, Validators.minLength(8)]),
    role: new FormControl<Role>('TRAVELER', { nonNullable: true, validators: Validators.required }),
    partnerType: new FormControl<PartnerType | null>(null),
  });

  protected isSubmitting = false;

  constructor() {
    // ------------ CONDITIONAL VALIDATOR: partnerType required only for PARTNER ------------
    this.registerForm.controls.role.valueChanges.subscribe((role) => {
      const partnerType = this.registerForm.controls.partnerType;

      if (role === 'PARTNER') {
        partnerType.setValidators(Validators.required);
      } else {
        partnerType.clearValidators();
        partnerType.setValue(null);
      }

      // Re-checks validity now that the rules themselves have changed -
      // setValidators()/clearValidators() alone don't trigger a re-check.
      partnerType.updateValueAndValidity();
    });
  }

  // ------------ METHOD 1: submit the register form ------------
  protected onSubmit(): void {
    if (this.registerForm.invalid) {
      return;
    }

    this.isSubmitting = true;
    const { email, password, role, partnerType } = this.registerForm.getRawValue();

    this.authService.register({ email: email!, password: password!, role, partnerType }).subscribe({
      next: () => this.router.navigate(['/']),
      error: () => {
        this.isSubmitting = false;
      },
    });
  }
}
