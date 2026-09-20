/**
 * Current-salary statistics for one group. Every amount is in `currency`; the backend never
 * combines currencies and the UI must not either.
 */
export interface SalaryStatistics {
  currency: string;
  employeeCount: number;
  averageSalary: number;
  medianSalary: number;
  minimumSalary: number;
  maximumSalary: number;
}

export type CurrencySalaryStatistics = SalaryStatistics;

export interface CountrySalaryStatistics extends SalaryStatistics {
  /** ISO 3166-1 alpha-2. */
  country: string;
}

export interface DepartmentSalaryStatistics extends SalaryStatistics {
  department: string;
}

/** Response of GET /api/analytics/overview. */
export interface AnalyticsOverview {
  /** ISO-8601 instant. */
  generatedAt: string;
  /** UTC business date "YYYY-MM-DD" whose salaries count as current. */
  asOfDate: string;
  currencies: CurrencySalaryStatistics[];
}
