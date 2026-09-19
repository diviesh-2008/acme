package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.acme.salary.employee.EmploymentStatus;
import org.junit.jupiter.api.Test;

class SalarySeedGeneratorTest {

	private final List<SeedEmployee> employees = new EmployeeSeedGenerator().generate();

	private final List<SeedSalary> salaries = new SalarySeedGenerator().generate(employees);

	private final Map<String, SeedEmployee> employeesByCode = employees.stream()
		.collect(Collectors.toMap(SeedEmployee::employeeCode, Function.identity()));

	@Test
	void everyRunProducesIdenticalData() {
		assertThat(new SalarySeedGenerator().generate(employees)).isEqualTo(salaries);
	}

	@Test
	void outputIsPinnedSoAccidentalChangesAreCaught() {
		assertThat(salaries).hasSize(24_901);
		// EMP-00001 is a Sales employee in Germany hired 2018-04-27 (see EmployeeSeedGeneratorTest).
		assertThat(salaries.getFirst()).isEqualTo(new SeedSalary("EMP-00001", new BigDecimal("53200.00"), "EUR",
				LocalDate.of(2018, 4, 27)));
	}

	@Test
	void everyEmployeeStartsOnTheirHireDate() {
		Map<String, LocalDate> firstRecord = salaries.stream()
			.collect(Collectors.toMap(SeedSalary::employeeCode, SeedSalary::effectiveDate, (a, b) -> a));

		assertThat(firstRecord).hasSize(10_000);
		assertThat(firstRecord).allSatisfy(
				(code, date) -> assertThat(date).isEqualTo(employeesByCode.get(code).hireDate()));
	}

	@Test
	void eachEmployeeHasOneToFiveRecordsWithUniqueDates() {
		Map<String, List<SeedSalary>> byEmployee = salaries.stream()
			.collect(Collectors.groupingBy(SeedSalary::employeeCode));

		assertThat(byEmployee.values()).allSatisfy(history -> {
			assertThat(history).hasSizeBetween(1, 5);
			assertThat(history).extracting(SeedSalary::effectiveDate).doesNotHaveDuplicates();
		});
	}

	@Test
	void includesFutureDatedRaisesButNotForTerminatedEmployees() {
		List<SeedSalary> scheduled = salaries.stream()
			.filter(s -> s.effectiveDate().equals(SalarySeedGenerator.SCHEDULED_RAISE_DATE))
			.toList();

		assertThat(scheduled).isNotEmpty();
		assertThat(scheduled).noneMatch(
				s -> employeesByCode.get(s.employeeCode()).employmentStatus() == EmploymentStatus.TERMINATED);
		assertThat(salaries).allMatch(s -> !s.effectiveDate().isAfter(SalarySeedGenerator.SCHEDULED_RAISE_DATE));
	}

	@Test
	void amountsArePositiveWholeNumbersWithTwoDecimalPlaces() {
		assertThat(salaries).allSatisfy(s -> {
			assertThat(s.amount()).isPositive();
			assertThat(s.amount().scale()).isEqualTo(2);
			assertThat(s.amount().stripTrailingZeros().scale()).isLessThanOrEqualTo(0);
		});
	}

	@Test
	void currenciesAreValidAndMostlyLocal() {
		assertThat(salaries).extracting(SeedSalary::currency)
			.allMatch(code -> Currency.getInstance(code) != null)
			.contains("USD", "INR", "GBP", "EUR", "AUD", "CAD", "SGD");
		long usdAbroad = salaries.stream()
			.filter(s -> s.currency().equals("USD") && !employeesByCode.get(s.employeeCode()).countryCode().equals("US"))
			.map(SeedSalary::employeeCode)
			.distinct()
			.count();
		assertThat(usdAbroad).isPositive().isLessThan(500);
	}

	@Test
	void anEmployeeKeepsOneCurrencyAcrossTheirHistory() {
		Map<String, Long> currenciesPerEmployee = salaries.stream()
			.collect(Collectors.groupingBy(SeedSalary::employeeCode,
					Collectors.mapping(SeedSalary::currency, Collectors.collectingAndThen(Collectors.toSet(),
							set -> (long) set.size()))));

		assertThat(currenciesPerEmployee.values()).containsOnly(1L);
	}

}
