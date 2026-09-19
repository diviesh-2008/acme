package com.acme.salary.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.acme.salary.analytics.SalaryStatisticsRepository.Grouping;
import com.acme.salary.analytics.dto.AnalyticsOverviewResponse;
import com.acme.salary.analytics.dto.CountrySalaryStatistics;
import com.acme.salary.analytics.dto.CurrencySalaryStatistics;
import com.acme.salary.analytics.dto.DepartmentSalaryStatistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compensation analytics over current salaries: the record with the latest effective date
 * on or before today, where "today" comes from the injected {@link Clock} (UTC).
 * TERMINATED employees and employees without a current salary are not counted.
 * <p>
 * Every figure is per currency. Amounts in different currencies are never combined, and
 * nothing is converted. Averages and medians are rounded once, to 2 decimal places, half up.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

	private static final int MONEY_SCALE = 2;

	private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

	private final SalaryStatisticsRepository repository;

	private final Clock clock;

	public AnalyticsService(SalaryStatisticsRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public AnalyticsOverviewResponse overview() {
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, clock.getZone());
		List<CurrencySalaryStatistics> currencies = repository.currentSalaryStatistics(Grouping.CURRENCY, today)
			.stream()
			.map(row -> new CurrencySalaryStatistics(row.currency(), row.employeeCount(), average(row), median(row),
					row.minimum(), row.maximum()))
			.toList();
		return new AnalyticsOverviewResponse(now, today, currencies);
	}

	/** Ordered by country code, then currency. */
	public List<CountrySalaryStatistics> byCountry() {
		return repository.currentSalaryStatistics(Grouping.COUNTRY, today())
			.stream()
			.map(row -> new CountrySalaryStatistics(row.dimension(), row.currency(), row.employeeCount(),
					average(row), median(row), row.minimum(), row.maximum()))
			.toList();
	}

	/** Ordered by department, then currency. */
	public List<DepartmentSalaryStatistics> byDepartment() {
		return repository.currentSalaryStatistics(Grouping.DEPARTMENT, today())
			.stream()
			.map(row -> new DepartmentSalaryStatistics(row.dimension(), row.currency(), row.employeeCount(),
					average(row), median(row), row.minimum(), row.maximum()))
			.toList();
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

	// Exact sum divided by count, rounded once. BigDecimal only; never floating point.
	private static BigDecimal average(SalaryStatisticsRow row) {
		return row.total().divide(BigDecimal.valueOf(row.employeeCount()), MONEY_SCALE, MONEY_ROUNDING);
	}

	// The mean of two 2-decimal amounts has at most 3 decimals, e.g. 100.015 -> 100.02.
	private static BigDecimal median(SalaryStatisticsRow row) {
		return row.median().setScale(MONEY_SCALE, MONEY_ROUNDING);
	}

}
