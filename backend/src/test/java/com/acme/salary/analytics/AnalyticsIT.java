package com.acme.salary.analytics;

import static com.acme.salary.IntegrationTest.HR_MANAGER_EMAIL;
import static com.acme.salary.IntegrationTest.HR_MANAGER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.acme.salary.IntegrationTest;
import com.acme.salary.analytics.dto.CountrySalaryStatistics;
import com.acme.salary.analytics.dto.CurrencySalaryStatistics;
import com.acme.salary.analytics.dto.DepartmentSalaryStatistics;
import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.EmploymentStatus;
import com.acme.salary.salary.SalaryRecord;
import com.acme.salary.salary.SalaryRecordRepository;
import com.acme.salary.salary.SalaryService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Analytics SQL against real MySQL: current-salary selection, per-currency grouping,
 * median positions and ordering.
 * <p>
 * The context holds the seeded employees and salaries. Fixtures use values the seed never
 * produces (currencies CHF, JPY and NZD; country NZ; department "Analytics Lab"), so
 * their groups contain only fixture data. Dates are 2001/2002 (current) and 2999
 * (future), so results do not depend on today's date. Each test is rolled back.
 */
@IntegrationTest
@Transactional
class AnalyticsIT {

	private static final String PAST = "2001-01-01";

	private static final String FUTURE = "2999-01-01";

	@Autowired
	private AnalyticsService analyticsService;

	@Autowired
	private SalaryService salaryService;

	@Autowired
	private EmployeeRepository employeeRepository;

	@Autowired
	private SalaryRecordRepository salaryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MockMvc mockMvc;

	private int fixtureCount;

	// --- median, average, min, max -------------------------------------------------------------

	@Test
	void oddCountMedianIsTheMiddleValue() {
		paid("CHF", "100.00", "300.00", "200.00");

		assertThat(overview("CHF")).contains(stats("CHF", 3, "200.00", "200.00", "100.00", "300.00"));
	}

	@Test
	void evenCountMedianIsTheMeanOfTheTwoMiddleValues() {
		paid("CHF", "400.00", "100.00", "300.00", "200.00");

		assertThat(overview("CHF")).contains(stats("CHF", 4, "250.00", "250.00", "100.00", "400.00"));
	}

	@Test
	void decimalAmountsAreRoundedOnceHalfUpToCents() {
		// median and average are both 100.015 exactly -> 100.02
		paid("CHF", "100.01", "100.02");
		// average 30.02 / 3 = 10.00666... -> 10.01; median 10.01
		paid("JPY", "10.00", "10.01", "10.01");

		assertThat(overview("CHF")).contains(stats("CHF", 2, "100.02", "100.02", "100.01", "100.02"));
		assertThat(overview("JPY")).contains(stats("JPY", 3, "10.01", "10.01", "10.00", "10.01"));
	}

	@Test
	void largestDecimal15And2AmountsAreHandledExactly() {
		paid("CHF", "9999999999999.99", "9999999999999.99", "0.01");

		assertThat(overview("CHF")).contains(
				stats("CHF", 3, "6666666666666.66", "9999999999999.99", "0.01", "9999999999999.99"));
	}

	@Test
	void oneEmployeeWithOneSalary() {
		paid("JPY", "1234.56");

		assertThat(overview("JPY")).contains(stats("JPY", 1, "1234.56", "1234.56", "1234.56", "1234.56"));
	}

	// --- which salary counts -------------------------------------------------------------------

	@Test
	void onlyTheCurrentSalaryCountsAndItMatchesTheSalaryApi() {
		Employee employee = employee("NZ", "Analytics Lab", EmploymentStatus.ACTIVE);
		salary(employee, "100.00", "CHF", PAST);
		salary(employee, "200.00", "CHF", "2002-01-01");
		salary(employee, "999.00", "CHF", FUTURE);

		assertThat(overview("CHF")).contains(stats("CHF", 1, "200.00", "200.00", "200.00", "200.00"));
		assertThat(salaryService.currentSalary(employee.getId()).amount()).isEqualByComparingTo("200.00");
	}

	@Test
	void employeesWithOnlyFutureSalaryOrNoSalaryAreExcluded() {
		salary(employee("NZ", "Analytics Lab", EmploymentStatus.ACTIVE), "500.00", "JPY", FUTURE);
		employee("NZ", "Analytics Lab", EmploymentStatus.ACTIVE);
		salary(employee("NZ", "Analytics Lab", EmploymentStatus.ACTIVE), "100.00", "NZD", PAST);

		assertThat(overview("JPY")).isEmpty();
		assertThat(byCountry("NZ")).extracting(CountrySalaryStatistics::currency, CountrySalaryStatistics::employeeCount)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("NZD", 1L));
	}

	@Test
	void terminatedEmployeesAreExcluded() {
		paid("CHF", "100.00");
		salary(employee("NZ", "Analytics Lab", EmploymentStatus.TERMINATED), "5000.00", "CHF", PAST);
		salary(employee("NZ", "Analytics Lab", EmploymentStatus.ON_LEAVE), "300.00", "CHF", PAST);

		assertThat(overview("CHF")).contains(stats("CHF", 2, "200.00", "200.00", "100.00", "300.00"));
	}

	// --- grouping ------------------------------------------------------------------------------

	@Test
	void aCountryWithTwoCurrenciesGetsTwoRowsThatAreNeverCombined() {
		salary(employee("NZ", "Analytics Lab", EmploymentStatus.ACTIVE), "100.00", "NZD", PAST);
		salary(employee("NZ", "Analytics Lab", EmploymentStatus.ACTIVE), "300.00", "NZD", PAST);
		salary(employee("NZ", "Analytics Lab", EmploymentStatus.ACTIVE), "1000.00", "CHF", PAST);

		assertThat(byCountry("NZ")).containsExactly(
				new CountrySalaryStatistics("NZ", "CHF", 1, money("1000.00"), money("1000.00"), money("1000.00"),
						money("1000.00")),
				new CountrySalaryStatistics("NZ", "NZD", 2, money("200.00"), money("200.00"), money("100.00"),
						money("300.00")));
	}

	@Test
	void differentCountriesAreSeparate() {
		salary(employee("NZ", "Analytics Lab", EmploymentStatus.ACTIVE), "100.00", "CHF", PAST);
		salary(employee("IE", "Analytics Lab", EmploymentStatus.ACTIVE), "900.00", "CHF", PAST);

		assertThat(byCountry("NZ")).singleElement().extracting(CountrySalaryStatistics::averageSalary)
			.isEqualTo(money("100.00"));
		assertThat(byCountry("IE")).singleElement().extracting(CountrySalaryStatistics::averageSalary)
			.isEqualTo(money("900.00"));
	}

	@Test
	void aDepartmentWithTwoCurrenciesGetsTwoRowsOrderedByCurrency() {
		salary(employee("NZ", "Analytics Lab", EmploymentStatus.ACTIVE), "100.00", "NZD", PAST);
		salary(employee("NZ", "Analytics Lab", EmploymentStatus.ACTIVE), "50.00", "CHF", PAST);

		assertThat(analyticsService.byDepartment().stream().filter(row -> row.department().equals("Analytics Lab")))
			.extracting(DepartmentSalaryStatistics::currency, DepartmentSalaryStatistics::employeeCount)
			.containsExactly(org.assertj.core.groups.Tuple.tuple("CHF", 1L),
					org.assertj.core.groups.Tuple.tuple("NZD", 1L));
	}

	@Test
	void resultsAreOrderedByGroupThenCurrency() {
		assertThat(analyticsService.overview().currencies()).extracting(CurrencySalaryStatistics::currency)
			.isSorted()
			.doesNotHaveDuplicates();
		assertThat(analyticsService.byCountry()).isSortedAccordingTo(
				Comparator.comparing(CountrySalaryStatistics::country).thenComparing(CountrySalaryStatistics::currency));
		assertThat(analyticsService.byDepartment()).isSortedAccordingTo(
				Comparator.comparing(DepartmentSalaryStatistics::department, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(DepartmentSalaryStatistics::currency));
	}

	// --- seeded data and empty database --------------------------------------------------------

	@Test
	void seededDataGivesSeveralCurrencyGroupsCountingEveryEligibleEmployeeOnce() {
		long eligible = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM employee e
				WHERE e.employment_status <> 'TERMINATED'
				  AND EXISTS (SELECT 1 FROM salary_record s WHERE s.employee_id = e.id AND s.effective_date <= ?)""",
				Long.class, LocalDate.now(ZoneOffset.UTC));

		List<CurrencySalaryStatistics> currencies = analyticsService.overview().currencies();

		assertThat(currencies).extracting(CurrencySalaryStatistics::currency)
			.contains("USD", "INR", "GBP", "EUR", "AUD", "CAD", "SGD");
		assertThat(currencies.stream().mapToLong(CurrencySalaryStatistics::employeeCount).sum()).isEqualTo(eligible);
		assertThat(analyticsService.byCountry().stream().filter(row -> row.country().equals("IN")))
			.extracting(CountrySalaryStatistics::currency)
			.contains("INR", "USD");
	}

	@Test
	void noCurrentSalariesGiveEmptyResults() {
		jdbcTemplate.update("DELETE FROM salary_record");

		assertThat(analyticsService.overview().currencies()).isEmpty();
		assertThat(analyticsService.byCountry()).isEmpty();
		assertThat(analyticsService.byDepartment()).isEmpty();
	}

	@Test
	void endpointsRequireAHrManagerToken() throws Exception {
		for (String path : List.of("/api/analytics/overview", "/api/analytics/by-country",
				"/api/analytics/by-department")) {
			mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		}

		String token = JsonPath.read(mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
					{"email": "%s", "password": "%s"}""".formatted(HR_MANAGER_EMAIL, HR_MANAGER_PASSWORD)))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.accessToken");

		mockMvc.perform(get("/api/analytics/overview").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.currencies").isArray());
		mockMvc.perform(get("/api/analytics/by-country").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/analytics/by-department").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());
	}

	// --- helpers -------------------------------------------------------------------------------

	/** One ACTIVE fixture employee per amount, each with a single current salary. */
	private void paid(String currency, String... amounts) {
		for (String amount : amounts) {
			salary(employee("NZ", "Analytics Lab", EmploymentStatus.ACTIVE), amount, currency, PAST);
		}
	}

	private Employee employee(String country, String department, EmploymentStatus status) {
		String code = "ANL-" + (++fixtureCount);
		return employeeRepository.saveAndFlush(new Employee(code, "Analytics", "Fixture",
				code.toLowerCase() + "@fixture.example", "Analyst", department, country, status,
				LocalDate.of(2000, 1, 1)));
	}

	private void salary(Employee employee, String amount, String currency, String effectiveDate) {
		salaryRepository.saveAndFlush(new SalaryRecord(employee.getId(), new BigDecimal(amount), currency,
				LocalDate.parse(effectiveDate), Instant.parse("2026-01-01T00:00:00Z")));
	}

	private Optional<CurrencySalaryStatistics> overview(String currency) {
		return analyticsService.overview().currencies().stream().filter(row -> row.currency().equals(currency))
			.findFirst();
	}

	private List<CountrySalaryStatistics> byCountry(String country) {
		return analyticsService.byCountry().stream().filter(row -> row.country().equals(country)).toList();
	}

	private static CurrencySalaryStatistics stats(String currency, long count, String average, String median,
			String minimum, String maximum) {
		return new CurrencySalaryStatistics(currency, count, money(average), money(median), money(minimum),
				money(maximum));
	}

	private static BigDecimal money(String amount) {
		return new BigDecimal(amount);
	}

}
