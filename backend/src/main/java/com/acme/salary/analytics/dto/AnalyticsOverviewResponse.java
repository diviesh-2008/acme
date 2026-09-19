package com.acme.salary.analytics.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * @param generatedAt when the figures were computed
 * @param asOfDate the date whose salaries count as current (UTC)
 * @param currencies one entry per currency, ordered by currency code; empty if no one has
 *        a current salary
 */
public record AnalyticsOverviewResponse(Instant generatedAt, LocalDate asOfDate,
		List<CurrencySalaryStatistics> currencies) {
}
