/** A salary record as returned by the salary endpoints. */
export interface SalaryRecord {
  id: number;
  amount: number;
  /** ISO 4217, e.g. "INR". */
  currency: string;
  /** Business date "YYYY-MM-DD". */
  effectiveDate: string;
}

/** Body of POST /api/employees/{employeeId}/salary. */
export interface CreateSalaryRequest {
  amount: number;
  currency: string;
  effectiveDate: string;
}

/**
 * Body of PUT /api/employees/{employeeId}/salary/{salaryId}.
 * There is deliberately no effectiveDate: it cannot be changed (the backend rejects it).
 */
export interface CorrectSalaryRequest {
  amount: number;
  currency: string;
}
