package com.acme.salary.analytics.dto;

import java.math.BigDecimal;

/**
 * Current-salary statistics for one department and currency. A department with employees
 * paid in several currencies has one of these per currency; they are never combined.
 */
public record DepartmentSalaryStatistics(String department, String currency, long employeeCount,
		BigDecimal averageSalary, BigDecimal medianSalary, BigDecimal minimumSalary, BigDecimal maximumSalary) {
}
