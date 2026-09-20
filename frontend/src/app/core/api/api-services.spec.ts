import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DEFAULT_EMPLOYEE_CRITERIA } from '../models/employee.models';
import { SalaryRecord } from '../models/salary.models';
import { EmployeeService } from './employee.service';
import { SalaryService } from './salary.service';

describe('API services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('EmployeeService', () => {
    it('sends only page and size when there are no filters', () => {
      TestBed.inject(EmployeeService).search(DEFAULT_EMPLOYEE_CRITERIA).subscribe();

      const request = http.expectOne((r) => r.url === '/api/employees');
      expect(request.request.params.keys()).toEqual(['page', 'size']);
      expect(request.request.params.get('page')).toBe('0');
      expect(request.request.params.get('size')).toBe('20');
    });

    it('sends trimmed search and every non-empty filter to the server', () => {
      TestBed.inject(EmployeeService)
        .search({ page: 2, size: 50, search: '  john ', country: 'US', department: 'Engineering', status: 'ACTIVE' })
        .subscribe();

      const params = http.expectOne((r) => r.url === '/api/employees').request.params;
      expect(params.get('page')).toBe('2');
      expect(params.get('size')).toBe('50');
      expect(params.get('search')).toBe('john');
      expect(params.get('country')).toBe('US');
      expect(params.get('department')).toBe('Engineering');
      expect(params.get('status')).toBe('ACTIVE');
    });
  });

  describe('SalaryService', () => {
    it('treats 404 on the current salary as "no salary in effect"', () => {
      let result: SalaryRecord | null | undefined;
      TestBed.inject(SalaryService).currentSalary(7).subscribe((value) => (result = value));

      http.expectOne('/api/employees/7/salary').flush({ status: 404 }, { status: 404, statusText: 'Not Found' });

      expect(result).toBeNull();
    });

    it('passes other failures of the current salary on', () => {
      let failed = false;
      TestBed.inject(SalaryService).currentSalary(7).subscribe({ error: () => (failed = true) });

      http.expectOne('/api/employees/7/salary').flush({}, { status: 500, statusText: 'Server Error' });

      expect(failed).toBeTrue();
    });

    it('sends a correction without an effective date', () => {
      const request = { amount: 960000, currency: 'INR', effectiveDate: '2030-01-01' } as unknown as {
        amount: number;
        currency: string;
      };
      TestBed.inject(SalaryService).correct(7, 15, request).subscribe();

      const put = http.expectOne('/api/employees/7/salary/15');
      expect(put.request.method).toBe('PUT');
      expect(put.request.body).toEqual({ amount: 960000, currency: 'INR' });
    });

    it('creates a salary with POST', () => {
      TestBed.inject(SalaryService).create(7, { amount: 950000, currency: 'INR', effectiveDate: '2026-10-01' }).subscribe();

      const post = http.expectOne('/api/employees/7/salary');
      expect(post.request.method).toBe('POST');
      expect(post.request.body).toEqual({ amount: 950000, currency: 'INR', effectiveDate: '2026-10-01' });
    });
  });
});
