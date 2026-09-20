import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AnalyticsOverview,
  CountrySalaryStatistics,
  DepartmentSalaryStatistics,
} from '../models/analytics.models';

/** Compensation analytics. Every figure is per currency; nothing is combined or converted. */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/analytics`;

  overview(): Observable<AnalyticsOverview> {
    return this.http.get<AnalyticsOverview>(`${this.baseUrl}/overview`);
  }

  byCountry(): Observable<CountrySalaryStatistics[]> {
    return this.http.get<CountrySalaryStatistics[]>(`${this.baseUrl}/by-country`);
  }

  byDepartment(): Observable<DepartmentSalaryStatistics[]> {
    return this.http.get<DepartmentSalaryStatistics[]>(`${this.baseUrl}/by-department`);
  }
}
