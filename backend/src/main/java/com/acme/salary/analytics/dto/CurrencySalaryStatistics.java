package com.acme.salary.analytics.dto;

import java.math.BigDecimal;

/**
 * Current-salary statistics for one currency. Every amount is in {@code currency}.
 */
public record CurrencySalaryStatistics(String currency, long employeeCount, BigDecimal averageSalary,
		BigDecimal medianSalary, BigDecimal minimumSalary, BigDecimal maximumSalary) {
}
