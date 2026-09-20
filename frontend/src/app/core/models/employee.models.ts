export type EmploymentStatus = 'ACTIVE' | 'ON_LEAVE' | 'TERMINATED';

export const EMPLOYMENT_STATUSES: readonly EmploymentStatus[] = ['ACTIVE', 'ON_LEAVE', 'TERMINATED'];

/** Response of GET /api/employees/{id} and each row of GET /api/employees. */
export interface Employee {
  id: number;
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string;
  jobTitle: string;
  department: string;
  /** ISO 3166-1 alpha-2, e.g. "IN". */
  countryCode: string;
  employmentStatus: EmploymentStatus;
  /** Business date "YYYY-MM-DD". */
  hireDate: string;
}

/** Query parameters of GET /api/employees. Empty strings mean "no filter". */
export interface EmployeeSearchCriteria {
  page: number;
  size: number;
  search: string;
  country: string;
  department: string;
  status: EmploymentStatus | '';
}

/** The backend rejects larger pages with 400. */
export const MAX_PAGE_SIZE = 100;

export const DEFAULT_EMPLOYEE_CRITERIA: EmployeeSearchCriteria = {
  page: 0,
  size: 20,
  search: '',
  country: '',
  department: '',
  status: '',
};
