import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CorrectSalaryRequest, CreateSalaryRequest, SalaryRecord } from '../models/salary.models';

/**
 * Salary history of one employee. There is no delete: salary records are never removed.
 * The backend decides which record is current; the UI never works it out itself.
 */
@Injectable({ providedIn: 'root' })
export class SalaryService {
  private readonly http = inject(HttpClient);

  /**
   * The salary in force today, or null when none has taken effect yet. Callers load the
   * employee separately, so a 404 here means "no current salary", not "no employee".
   */
  currentSalary(employeeId: number): Observable<SalaryRecord | null> {
    return this.http.get<SalaryRecord>(this.url(employeeId)).pipe(
      catchError((error: unknown) =>
        error instanceof HttpErrorResponse && error.status === 404 ? of(null) : throwError(() => error),
      ),
    );
  }

  /** All records, newest effective date first (backend order). */
  history(employeeId: number): Observable<SalaryRecord[]> {
    return this.http.get<SalaryRecord[]>(`${this.url(employeeId)}/history`);
  }

  create(employeeId: number, request: CreateSalaryRequest): Observable<SalaryRecord> {
    return this.http.post<SalaryRecord>(this.url(employeeId), request);
  }

  /** Changes amount and currency only; the effective date is never sent. */
  correct(employeeId: number, salaryId: number, request: CorrectSalaryRequest): Observable<SalaryRecord> {
    const body: CorrectSalaryRequest = { amount: request.amount, currency: request.currency };
    return this.http.put<SalaryRecord>(`${this.url(employeeId)}/${salaryId}`, body);
  }

  private url(employeeId: number): string {
    return `${environment.apiBaseUrl}/employees/${employeeId}/salary`;
  }
}
