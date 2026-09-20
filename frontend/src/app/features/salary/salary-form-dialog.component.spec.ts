import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { salary } from '../../testing/fixtures';
import { SalaryFormDialogComponent, SalaryFormDialogData } from './salary-form-dialog.component';

describe('SalaryFormDialogComponent', () => {
  let fixture: ComponentFixture<SalaryFormDialogComponent>;
  let http: HttpTestingController;
  let close: jasmine.Spy;

  function setup(data: SalaryFormDialogData): void {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [SalaryFormDialogComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SalaryFormDialogComponent);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  const createData: SalaryFormDialogData = { mode: 'create', employeeId: 7, employeeName: 'Priya Sharma (EMP-00007)', suggestedCurrency: 'INR' };
  const record = salary(15, '2026-01-01', 900000);

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function save(): void {
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button[type=submit]')?.click();
    fixture.detectChanges();
  }

  describe('adding a salary', () => {
    beforeEach(() => setup(createData));

    it('checks the format of amount, currency and date before sending', () => {
      const form = fixture.componentInstance.form;
      const invalid: [string, string, string, string][] = [
        ['', 'INR', '2026-10-01', 'required'],
        ['0', 'INR', '2026-10-01', 'positive'],
        ['0.00', 'INR', '2026-10-01', 'positive'],
        ['-5', 'INR', '2026-10-01', 'pattern'],
        ['1.234', 'INR', '2026-10-01', 'pattern'],
        ['12345678901234', 'INR', '2026-10-01', 'pattern'],
        ['abc', 'INR', '2026-10-01', 'pattern'],
      ];
      for (const [amount, currency, effectiveDate, error] of invalid) {
        form.setValue({ amount, currency, effectiveDate });
        expect(form.controls.amount.hasError(error)).withContext(amount).toBeTrue();
      }

      form.setValue({ amount: '950000.5', currency: 'IN', effectiveDate: '' });
      expect(form.controls.amount.valid).toBeTrue();
      expect(form.controls.currency.hasError('pattern')).toBeTrue();
      expect(form.controls.effectiveDate.hasError('required')).toBeTrue();

      save();
      http.expectNone('/api/employees/7/salary');
    });

    it('suggests the current currency', () => {
      expect(fixture.componentInstance.form.controls.currency.value).toBe('INR');
    });

    it('sends the new record and closes with the saved result', () => {
      fixture.componentInstance.form.setValue({ amount: '950000', currency: 'usd', effectiveDate: '2026-10-01' });

      save();

      const post = http.expectOne('/api/employees/7/salary');
      expect(post.request.method).toBe('POST');
      expect(post.request.body).toEqual({ amount: 950000, currency: 'USD', effectiveDate: '2026-10-01' });
      post.flush(salary(20, '2026-10-01', 950000, 'USD'), { status: 201, statusText: 'Created' });
      expect(close).toHaveBeenCalledWith(salary(20, '2026-10-01', 950000, 'USD'));
    });

    it("shows the backend's conflict message for 409 and stays open", () => {
      fixture.componentInstance.form.setValue({ amount: '950000', currency: 'INR', effectiveDate: '2026-01-01' });
      save();

      http.expectOne('/api/employees/7/salary').flush(
        { status: 409, detail: 'Employee 7 already has a salary record effective on 2026-01-01. Correct that record instead.' },
        { status: 409, statusText: 'Conflict' },
      );
      fixture.detectChanges();

      expect(text()).toContain('Employee 7 already has a salary record effective on 2026-01-01. Correct that record instead.');
      expect(close).not.toHaveBeenCalled();
    });

    it('shows backend business-rule errors next to the field', () => {
      fixture.componentInstance.form.setValue({ amount: '950000', currency: 'INR', effectiveDate: '2019-01-01' });
      save();

      http.expectOne('/api/employees/7/salary').flush(
        { status: 400, detail: 'One or more fields are invalid.', errors: { effectiveDate: "must not be before the employee's hire date (2020-03-01)" } },
        { status: 400, statusText: 'Bad Request' },
      );
      fixture.detectChanges();

      expect(fixture.componentInstance.form.controls.effectiveDate.getError('server')).toContain('hire date');
      expect(text()).toContain("must not be before the employee's hire date (2020-03-01)");
      expect(close).not.toHaveBeenCalled();
    });
  });

  describe('correcting a salary', () => {
    beforeEach(() => setup({ mode: 'correct', employeeId: 7, employeeName: 'Priya Sharma (EMP-00007)', record }));

    it('only lets amount and currency change; the effective date is shown, not editable', () => {
      const element = fixture.nativeElement as HTMLElement;
      expect(element.querySelector('input[type=date]')).toBeNull();
      expect(text()).toContain('1 Jan 2026');
      expect(text()).toContain('The effective date cannot be changed.');
      expect(fixture.componentInstance.form.controls.amount.value).toBe('900000.00');
      expect(fixture.componentInstance.form.controls.currency.value).toBe('INR');
    });

    it('sends only amount and currency', () => {
      fixture.componentInstance.form.controls.amount.setValue('960000');

      save();

      const put = http.expectOne('/api/employees/7/salary/15');
      expect(put.request.method).toBe('PUT');
      expect(put.request.body).toEqual({ amount: 960000, currency: 'INR' });
      expect(Object.keys(put.request.body as object)).not.toContain('effectiveDate');
      put.flush(salary(15, '2026-01-01', 960000));
      expect(close).toHaveBeenCalledWith(salary(15, '2026-01-01', 960000));
    });
  });
});
