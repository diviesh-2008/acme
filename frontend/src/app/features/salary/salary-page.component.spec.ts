import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { SalaryRecord } from '../../core/models/salary.models';
import { NotificationService } from '../../core/notifications/notification.service';
import { employee, salary } from '../../testing/fixtures';
import { SalaryPageComponent } from './salary-page.component';

describe('SalaryPageComponent', () => {
  const past = salary(12, '2025-01-01', 800000);
  const current = salary(15, '2026-01-01', 900000);
  const future = salary(16, '2027-01-01', 1000000);

  let fixture: ComponentFixture<SalaryPageComponent>;
  let http: HttpTestingController;
  let openDialog: jasmine.Spy;
  let notifySuccess: jasmine.Spy;

  beforeEach(() => {
    openDialog = jasmine.createSpy('open');
    notifySuccess = jasmine.createSpy('success');
    TestBed.configureTestingModule({
      imports: [SalaryPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '7' })) } },
        { provide: MatDialog, useValue: { open: openDialog } },
        { provide: NotificationService, useValue: { success: notifySuccess, error: jasmine.createSpy('error') } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SalaryPageComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function respond(currentSalary: SalaryRecord | null, history: SalaryRecord[], status = employee()): void {
    http.expectOne('/api/employees/7').flush(status);
    const currentRequest = http.expectOne('/api/employees/7/salary');
    if (currentSalary) {
      currentRequest.flush(currentSalary);
    } else {
      currentRequest.flush({ detail: 'no salary' }, { status: 404, statusText: 'Not Found' });
    }
    http.expectOne('/api/employees/7/salary/history').flush(history);
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function buttons(): HTMLButtonElement[] {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
  }

  it('shows the current salary returned by the backend, with its currency', () => {
    respond(current, [future, current, past]);

    const card = (fixture.nativeElement as HTMLElement).querySelector('.current-card')?.textContent ?? '';
    expect(card).toContain('₹900,000.00 INR');
    expect(card).toContain('Effective since 1 Jan 2026');
  });

  it('lists the history newest first and marks current, future and past records', () => {
    respond(current, [future, current, past]);

    const rows = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tr.mat-mdc-row')).map(
      (row) => row.textContent ?? '',
    );
    expect(rows.length).toBe(3);
    expect(rows[0]).toContain('1 Jan 2027');
    expect(rows[0]).toContain('Future');
    expect(rows[1]).toContain('Current');
    expect(rows[2]).toContain('Past');
    expect(rows[2]).toContain('₹800,000.00 INR');
  });

  it('explains when no salary is in effect yet but one is scheduled', () => {
    respond(null, [future]);

    expect(text()).toContain('No salary in effect');
    expect(text()).toContain('A future-dated salary is scheduled');
    expect(text()).not.toContain('₹0');
  });

  it('shows an empty history for an employee without salary records', () => {
    respond(null, []);

    expect(text()).toContain('No salary history');
  });

  it('offers corrections but never deletion', () => {
    respond(current, [future, current, past]);

    const labels = buttons().map((button) => button.textContent ?? '');
    expect(labels.filter((label) => label.includes('Correct')).length).toBe(3);
    expect(labels.some((label) => /delete|remove/i.test(label))).toBeFalse();
  });

  it('reloads everything from the backend after a salary is added', () => {
    respond(current, [current]);
    openDialog.and.returnValue({ afterClosed: () => of(salary(20, '2026-10-01', 950000)) });

    buttons().find((button) => button.textContent?.includes('Add salary'))?.click();

    expect(openDialog).toHaveBeenCalled();
    expect(openDialog.calls.mostRecent().args[1].data).toEqual(
      jasmine.objectContaining({ mode: 'create', employeeId: 7, suggestedCurrency: 'INR' }),
    );
    expect(notifySuccess).toHaveBeenCalledWith('Salary record added.');
    respond(current, [salary(20, '2026-10-01', 950000), current]);
    expect(text()).toContain('₹950,000.00 INR');
  });

  it('opens the correction dialog for the chosen record and reloads afterwards', () => {
    respond(current, [current, past]);
    openDialog.and.returnValue({ afterClosed: () => of(salary(12, '2025-01-01', 810000)) });

    buttons().filter((button) => button.textContent?.includes('Correct'))[1].click();

    expect(openDialog.calls.mostRecent().args[1].data).toEqual(jasmine.objectContaining({ mode: 'correct', record: past }));
    expect(notifySuccess).toHaveBeenCalledWith('Salary record corrected.');
    respond(current, [current, salary(12, '2025-01-01', 810000)]);
  });

  it('does not reload when the dialog is cancelled', () => {
    respond(current, [current]);
    openDialog.and.returnValue({ afterClosed: () => of(undefined) });

    buttons().find((button) => button.textContent?.includes('Add salary'))?.click();

    expect(notifySuccess).not.toHaveBeenCalled();
    http.expectNone('/api/employees/7');
  });

  it('disables adding salaries for terminated employees', () => {
    respond(current, [current], employee({ employmentStatus: 'TERMINATED' }));

    expect(buttons().find((button) => button.textContent?.includes('Add salary'))?.disabled).toBeTrue();
  });

  it('shows an error state when the employee does not exist', () => {
    const salaryRequests = [http.expectOne('/api/employees/7/salary'), http.expectOne('/api/employees/7/salary/history')];
    http.expectOne('/api/employees/7').flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(text()).toContain('Employee not found.');
    // The other requests are abandoned as soon as the page knows the employee is missing.
    expect(salaryRequests.every((request) => request.cancelled)).toBeTrue();
  });
});
