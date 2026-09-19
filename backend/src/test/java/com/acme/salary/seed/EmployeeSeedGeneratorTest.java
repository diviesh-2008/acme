package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.acme.salary.employee.EmploymentStatus;
import org.junit.jupiter.api.Test;

class EmployeeSeedGeneratorTest {

	private final List<SeedEmployee> employees = new EmployeeSeedGenerator().generate();

	@Test
	void generatesExactlyTenThousandEmployees() {
		assertThat(employees).hasSize(10_000);
	}

	@Test
	void everyRunProducesIdenticalData() {
		assertThat(new EmployeeSeedGenerator().generate()).isEqualTo(employees);
	}

	@Test
	void outputIsPinnedSoAccidentalChangesAreCaught() {
		assertThat(employees.getFirst()).isEqualTo(new SeedEmployee("EMP-00001", "Amelia", "Moore",
				"amelia.moore1@acme.example", "Account Executive", "Sales", "DE", EmploymentStatus.ACTIVE,
				LocalDate.of(2018, 4, 27)));
	}

	@Test
	void employeeCodesAreSequentialAndUnique() {
		assertThat(employees.getFirst().employeeCode()).isEqualTo("EMP-00001");
		assertThat(employees.getLast().employeeCode()).isEqualTo("EMP-10000");
		assertThat(employees).extracting(SeedEmployee::employeeCode).doesNotHaveDuplicates();
	}

	@Test
	void emailsAreUniqueAndUseTheReservedDomain() {
		assertThat(employees).extracting(SeedEmployee::email).doesNotHaveDuplicates()
			.allMatch(email -> email.endsWith("@acme.example"));
	}

	@Test
	void countryCodesAreValidIsoCodesFromSeveralCountries() {
		Set<String> countries = employees.stream().map(SeedEmployee::countryCode).collect(Collectors.toSet());

		assertThat(countries).hasSizeGreaterThanOrEqualTo(5)
			.isSubsetOf(Set.of(Locale.getISOCountries()));
	}

	@Test
	void coversEveryStatusAndSeveralDepartments() {
		assertThat(employees).extracting(SeedEmployee::employmentStatus)
			.contains(EmploymentStatus.values());
		assertThat(employees.stream().map(SeedEmployee::department).distinct().count()).isGreaterThanOrEqualTo(5);
	}

	@Test
	void hireDatesFallWithinTheFixedRange() {
		assertThat(employees).extracting(SeedEmployee::hireDate)
			.allMatch(date -> !date.isBefore(EmployeeSeedGenerator.EARLIEST_HIRE_DATE)
					&& date.isBefore(EmployeeSeedGenerator.REFERENCE_DATE));
	}

	@Test
	void valuesFitTheDatabaseColumns() {
		assertThat(employees).allSatisfy(employee -> {
			assertThat(employee.employeeCode()).hasSizeLessThanOrEqualTo(20);
			assertThat(employee.firstName()).hasSizeLessThanOrEqualTo(100);
			assertThat(employee.lastName()).hasSizeLessThanOrEqualTo(100);
			assertThat(employee.email()).hasSizeLessThanOrEqualTo(255);
			assertThat(employee.jobTitle()).hasSizeLessThanOrEqualTo(100);
			assertThat(employee.department()).hasSizeLessThanOrEqualTo(100);
			assertThat(employee.countryCode()).matches("[A-Z]{2}");
		});
	}

}
