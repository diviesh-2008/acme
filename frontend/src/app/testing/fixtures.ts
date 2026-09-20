import { AnalyticsOverview, CountrySalaryStatistics, DepartmentSalaryStatistics } from '../core/models/analytics.models';
import { Employee } from '../core/models/employee.models';
import { PageResponse } from '../core/models/page.models';
import { SalaryRecord } from '../core/models/salary.models';

/** Test data shaped exactly like the backend's JSON responses. */

export function employee(overrides: Partial<Employee> = {}): Employee {
  return {
    id: 7,
    employeeCode: 'EMP-00007',
    firstName: 'Priya',
    lastName: 'Sharma',
    email: 'priya.sharma7@acme.example',
    jobTitle: 'Software Engineer',
    department: 'Engineering',
    countryCode: 'IN',
    employmentStatus: 'ACTIVE',
    hireDate: '2020-03-01',
    ...overrides,
  };
}

export function page<T>(content: T[], overrides: Partial<PageResponse<T>> = {}): PageResponse<T> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    hasNext: false,
    hasPrevious: false,
    ...overrides,
  };
}

export function salary(id: number, effectiveDate: string, amount: number, currency = 'INR'): SalaryRecord {
  return { id, amount, currency, effectiveDate };
}

export function overview(overrides: Partial<AnalyticsOverview> = {}): AnalyticsOverview {
  return {
    generatedAt: '2026-09-19T18:39:41.687Z',
    asOfDate: '2026-09-19',
    currencies: [
      { currency: 'INR', employeeCount: 1433, averageSalary: 2759634.33, medianSalary: 2738000, minimumSalary: 1361000, maximumSalary: 4689000 },
      { currency: 'USD', employeeCount: 2999, averageSalary: 126293.86, medianSalary: 125100, minimumSalary: 61600, maximumSalary: 209700 },
    ],
    ...overrides,
  };
}

export function byCountry(): CountrySalaryStatistics[] {
  return [
    { country: 'IN', currency: 'INR', employeeCount: 1433, averageSalary: 2759634.33, medianSalary: 2738000, minimumSalary: 1361000, maximumSalary: 4689000 },
    { country: 'IN', currency: 'USD', employeeCount: 33, averageSalary: 127551.52, medianSalary: 121600, minimumSalary: 89600, maximumSalary: 176000 },
    { country: 'US', currency: 'USD', employeeCount: 2814, averageSalary: 126387.99, medianSalary: 125400, minimumSalary: 61600, maximumSalary: 209700 },
  ];
}

export function byDepartment(): DepartmentSalaryStatistics[] {
  return [
    { department: 'Engineering', currency: 'GBP', employeeCount: 450, averageSalary: 80000, medianSalary: 79500, minimumSalary: 45000, maximumSalary: 121300 },
    { department: 'Engineering', currency: 'INR', employeeCount: 500, averageSalary: 3100000, medianSalary: 3050000, minimumSalary: 1800000, maximumSalary: 4689000 },
  ];
}
