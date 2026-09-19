package com.acme.salary.analytics.dto;

import java.math.BigDecimal;

/**
 * Current-salary statistics for one country and currency. A country whose employees are
 * paid in two currencies has two of these; they are never combined.
 */
public record CountrySalaryStatistics(String country, String currency, long employeeCount, BigDecimal averageSalary,
		BigDecimal medianSalary, BigDecimal minimumSalary, BigDecimal maximumSalary) {
}
