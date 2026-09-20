import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Employee, EmployeeSearchCriteria } from '../models/employee.models';
import { PageResponse } from '../models/page.models';

/** Read-only employee API. Paging, search and filters all run on the server. */
@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/employees`;

  search(criteria: EmployeeSearchCriteria): Observable<PageResponse<Employee>> {
    let params = new HttpParams().set('page', criteria.page).set('size', criteria.size);
    const optional: [string, string][] = [
      ['search', criteria.search.trim()],
      ['country', criteria.country],
      ['department', criteria.department],
      ['status', criteria.status],
    ];
    for (const [name, value] of optional) {
      if (value !== '') {
        params = params.set(name, value);
      }
    }
    return this.http.get<PageResponse<Employee>>(this.baseUrl, { params });
  }

  getById(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.baseUrl}/${id}`);
  }
}
