import { Injectable } from '@angular/core';

import { DEFAULT_EMPLOYEE_CRITERIA, EmployeeSearchCriteria } from '../../core/models/employee.models';

/**
 * Remembers the last search, filters and page while the app is open, so returning from an
 * employee's page shows the same list.
 */
@Injectable({ providedIn: 'root' })
export class EmployeeListState {
  criteria: EmployeeSearchCriteria = { ...DEFAULT_EMPLOYEE_CRITERIA };
}
