package com.acme.salary.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.acme.salary.analytics.SalaryStatisticsRepository.Grouping;
import com.acme.salary.analytics.dto.AnalyticsOverviewResponse;
import com.acme.salary.analytics.dto.CountrySalaryStatistics;
import com.acme.salary.analytics.dto.CurrencySalaryStatistics;
import com.acme.salary.analytics.dto.DepartmentSalaryStatistics;
import org.junit.jupiter.api.Test;

/**
 * Service behaviour with the repository mocked and "today" fixed. The SQL itself (current
 * salary selection, grouping, median positions) is checked against MySQL in AnalyticsIT.
 */
class AnalyticsServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-20T08:30:00Z");

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);

	private final SalaryStatisticsRepository repository = mock(SalaryStatisticsRepository.class);

	private final AnalyticsService service = new AnalyticsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void overviewUsesTheClockForTodayAndGeneratedAt() {
		when(repository.currentSalaryStatistics(Grouping.CURRENCY, TODAY)).thenReturn(List.of());

		AnalyticsOverviewResponse overview = service.overview();

		assertThat(overview.generatedAt()).isEqualTo(NOW);
		assertThat(overview.asOfDate()).isEqualTo(TODAY);
		verify(repository).currentSalaryStatistics(Grouping.CURRENCY, TODAY);
	}

	@Test
	void eachCurrencyStaysSeparateAndInRepositoryOrder() {
		when(repository.currentSalaryStatistics(Grouping.CURRENCY, TODAY)).thenReturn(List.of(
				row(null, "INR", 3, "2700000.00", "800000.00", "1000000.00", "900000.000000"),
				row(null, "USD", 2, "160000.00", "70000.00", "90000.00", "80000.000000")));

		List<CurrencySalaryStatistics> currencies = service.overview().currencies();

		assertThat(currencies).containsExactly(
				new CurrencySalaryStatistics("INR", 3, money("900000.00"), money("900000.00"), money("800000.00"),
						money("1000000.00")),
				new CurrencySalaryStatistics("USD", 2, money("80000.00"), money("80000.00"), money("70000.00"),
						money("90000.00")));
	}

	@Test
	void averageIsTheExactSumDividedByCountRoundedOnceHalfUp() {
		// 1000.00 / 3 = 333.333... and 200.03 / 2 = 100.015
		when(repository.currentSalaryStatistics(Grouping.CURRENCY, TODAY)).thenReturn(List.of(
				row(null, "CHF", 3, "1000.00", "1.00", "998.00", "1.000000"),
				row(null, "JPY", 2, "200.03", "100.01", "100.02", "100.015000")));

		List<CurrencySalaryStatistics> currencies = service.overview().currencies();

		assertThat(currencies.get(0).averageSalary()).isEqualTo(money("333.33"));
		assertThat(currencies.get(1).averageSalary()).isEqualTo(money("100.02"));
	}

	@Test
	void medianOfTwoMiddleValuesIsRoundedHalfUpToCents() {
		when(repository.currentSalaryStatistics(Grouping.CURRENCY, TODAY))
			.thenReturn(List.of(row(null, "CHF", 2, "200.03", "100.01", "100.02", "100.015000")));

		assertThat(service.overview().currencies().getFirst().medianSalary()).isEqualTo(money("100.02"));
	}

	@Test
	void largestDecimal15And2AmountsKeepFullPrecision() {
		when(repository.currentSalaryStatistics(Grouping.CURRENCY, TODAY)).thenReturn(List.of(row(null, "IDR", 2,
				"19999999999999.98", "9999999999999.99", "9999999999999.99", "9999999999999.990000")));

		CurrencySalaryStatistics idr = service.overview().currencies().getFirst();

		assertThat(idr.averageSalary()).isEqualTo(money("9999999999999.99"));
		assertThat(idr.medianSalary()).isEqualTo(money("9999999999999.99"));
		assertThat(idr.maximumSalary()).isEqualTo(money("9999999999999.99"));
	}

	@Test
	void noCurrentSalariesGiveAnEmptyListNotZeroes() {
		when(repository.currentSalaryStatistics(Grouping.CURRENCY, TODAY)).thenReturn(List.of());
		when(repository.currentSalaryStatistics(Grouping.COUNTRY, TODAY)).thenReturn(List.of());
		when(repository.currentSalaryStatistics(Grouping.DEPARTMENT, TODAY)).thenReturn(List.of());

		assertThat(service.overview().currencies()).isNotNull().isEmpty();
		assertThat(service.byCountry()).isEmpty();
		assertThat(service.byDepartment()).isEmpty();
	}

	@Test
	void byCountryKeepsOneRowPerCountryAndCurrency() {
		when(repository.currentSalaryStatistics(Grouping.COUNTRY, TODAY)).thenReturn(List.of(
				row("IN", "INR", 2, "1800000.00", "800000.00", "1000000.00", "900000.000000"),
				row("IN", "USD", 1, "90000.00", "90000.00", "90000.00", "90000.000000")));

		assertThat(service.byCountry()).containsExactly(
				new CountrySalaryStatistics("IN", "INR", 2, money("900000.00"), money("900000.00"),
						money("800000.00"), money("1000000.00")),
				new CountrySalaryStatistics("IN", "USD", 1, money("90000.00"), money("90000.00"), money("90000.00"),
						money("90000.00")));
	}

	@Test
	void byDepartmentKeepsOneRowPerDepartmentAndCurrency() {
		when(repository.currentSalaryStatistics(Grouping.DEPARTMENT, TODAY)).thenReturn(List.of(
				row("Engineering", "GBP", 1, "70000.00", "70000.00", "70000.00", "70000.000000"),
				row("Engineering", "USD", 1, "120000.00", "120000.00", "120000.00", "120000.000000")));

		assertThat(service.byDepartment()).extracting(DepartmentSalaryStatistics::department,
				DepartmentSalaryStatistics::currency, DepartmentSalaryStatistics::averageSalary)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("Engineering", "GBP", money("70000.00")),
					org.assertj.core.groups.Tuple.tuple("Engineering", "USD", money("120000.00")));
	}

	private static SalaryStatisticsRow row(String dimension, String currency, long count, String total,
			String minimum, String maximum, String median) {
		return new SalaryStatisticsRow(dimension, currency, count, new BigDecimal(total), new BigDecimal(minimum),
				new BigDecimal(maximum), new BigDecimal(median));
	}

	private static BigDecimal money(String amount) {
		return new BigDecimal(amount);
	}

}
