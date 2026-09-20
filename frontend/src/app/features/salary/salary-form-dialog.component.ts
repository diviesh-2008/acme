import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Observable, finalize } from 'rxjs';

import { SalaryService } from '../../core/api/salary.service';
import { describeHttpError, fieldErrors } from '../../core/http/http-errors';
import { SalaryRecord } from '../../core/models/salary.models';
import { BusinessDatePipe, MoneyPipe } from '../../shared/pipes';

export type SalaryFormDialogData =
  | { mode: 'create'; employeeId: number; employeeName: string; suggestedCurrency: string | null }
  | { mode: 'correct'; employeeId: number; employeeName: string; record: SalaryRecord };

/** Up to 13 digits before the point and 2 after, as DECIMAL(15,2) allows. */
const AMOUNT_PATTERN = /^\d{1,13}(\.\d{1,2})?$/;

function positiveAmount(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  if (value === '' || !AMOUNT_PATTERN.test(value)) {
    return null;
  }
  return Number(value) > 0 ? null : { positive: true };
}

/**
 * Adds a salary record, or corrects the amount and currency of an existing one. The form
 * checks format only; the backend enforces the business rules (hire date, one year ahead,
 * terminated employees, duplicates, valid ISO currency) and its messages are shown here.
 * Closes with the saved record.
 */
@Component({
  selector: 'app-salary-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    BusinessDatePipe,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './salary-form-dialog.component.html',
  styleUrl: './salary-form-dialog.component.scss',
})
export class SalaryFormDialogComponent {
  readonly data = inject<SalaryFormDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<SalaryFormDialogComponent, SalaryRecord>>(MatDialogRef);
  private readonly salaries = inject(SalaryService);

  readonly form = inject(NonNullableFormBuilder).group({
    amount: [
      this.data.mode === 'correct' ? this.data.record.amount.toFixed(2) : '',
      [Validators.required, Validators.pattern(AMOUNT_PATTERN), positiveAmount],
    ],
    currency: [
      this.data.mode === 'correct' ? this.data.record.currency : (this.data.suggestedCurrency ?? ''),
      [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)],
    ],
    // Only used when adding; a correction can never change the effective date.
    effectiveDate: ['', this.data.mode === 'create' ? Validators.required : []],
  });

  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);
    this.saving.set(true);
    this.request()
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (saved) => this.dialogRef.close(saved),
        error: (error: unknown) => this.showServerError(error),
      });
  }

  private request(): Observable<SalaryRecord> {
    const value = this.form.getRawValue();
    const amount = Number(value.amount.trim());
    const currency = value.currency.trim().toUpperCase();
    if (this.data.mode === 'create') {
      return this.salaries.create(this.data.employeeId, { amount, currency, effectiveDate: value.effectiveDate });
    }
    return this.salaries.correct(this.data.employeeId, this.data.record.id, { amount, currency });
  }

  // Shows backend field messages next to their fields, and everything else above the form.
  private showServerError(error: unknown): void {
    const perField = fieldErrors(error);
    let unmatched = false;
    for (const [field, message] of Object.entries(perField)) {
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ server: message });
        control.markAsTouched();
      } else {
        unmatched = true;
      }
    }
    if (Object.keys(perField).length === 0 || unmatched) {
      this.errorMessage.set(describeHttpError(error, { notFound: 'This employee or salary record no longer exists.' }));
    }
  }
}
