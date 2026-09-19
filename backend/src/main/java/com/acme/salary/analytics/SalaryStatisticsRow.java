package com.acme.salary.analytics;

import java.math.BigDecimal;

/**
 * Raw per-group figures from MySQL, before rounding.
 *
 * @param dimension the country code or department, or {@code null} for the overview
 * @param total sum of the group's current salaries (exact)
 * @param median middle value, or the mean of the two middle values (exact, before rounding)
 */
record SalaryStatisticsRow(String dimension, String currency, long employeeCount, BigDecimal total,
		BigDecimal minimum, BigDecimal maximum, BigDecimal median) {
}
