package com.acme.salary.seed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.acme.salary.employee.EmploymentStatus;

/**
 * Generates the same salary history for the seeded employees on every run.
 * <p>
 * Every employee gets a starting salary on their hire date, plus 0–3 raises on
 * 1 January of recent years (up to {@link EmployeeSeedGenerator#REFERENCE_DATE}). About 5%
 * of non-terminated employees also have a raise scheduled for {@link #SCHEDULED_RAISE_DATE}.
 * Amounts are whole numbers in the employee's local currency, except for about 3% of
 * non-US employees paid in USD. The figures are typical local pay levels, not converted
 * amounts; the application never converts currencies.
 */
final class SalarySeedGenerator {

	static final long RANDOM_SEED = 20_260_102L;

	/** A future-dated raise, so the data shows scheduled salary changes. */
	static final LocalDate SCHEDULED_RAISE_DATE = LocalDate.of(2027, 1, 1);

	private static final int USD_ABROAD_PERCENT = 3;

	private static final int SCHEDULED_RAISE_PERCENT = 5;

	private static final Map<String, String> LOCAL_CURRENCY = Map.of("US", "USD", "IN", "INR", "GB", "GBP", "DE",
			"EUR", "FR", "EUR", "AU", "AUD", "CA", "CAD", "SG", "SGD");

	/** Typical mid-level annual salary, in the country's own currency. */
	private static final Map<String, Long> COUNTRY_BASE_SALARY = Map.of("US", 110_000L, "IN", 2_400_000L, "GB",
			65_000L, "DE", 70_000L, "FR", 60_000L, "AU", 120_000L, "CA", 100_000L, "SG", 105_000L);

	/** Department pay relative to the country base, in percent. */
	private static final Map<String, Integer> DEPARTMENT_PERCENT = Map.of("Engineering", 115, "Legal", 120,
			"Product", 110, "Finance", 100, "Sales", 95, "Marketing", 90, "Human Resources", 90, "Operations", 85,
			"Customer Support", 70);

	List<SeedSalary> generate(List<SeedEmployee> employees) {
		Random random = new Random(RANDOM_SEED);
		List<SeedSalary> salaries = new ArrayList<>();

		// Every employee consumes its draws in the same order; do not reorder them.
		for (SeedEmployee employee : employees) {
			boolean paidInUsd = !employee.countryCode().equals("US") && random.nextInt(100) < USD_ABROAD_PERCENT;
			String currency = paidInUsd ? "USD" : LOCAL_CURRENCY.get(employee.countryCode());
			long base = COUNTRY_BASE_SALARY.get(paidInUsd ? "US" : employee.countryCode());
			long amount = roundTo(base * DEPARTMENT_PERCENT.get(employee.department()) / 100
					* (80 + random.nextInt(51)) / 100, currency);
			salaries.add(new SeedSalary(employee.employeeCode(), money(amount), currency, employee.hireDate()));

			// Raises on 1 January of the most recent years after hire, oldest first.
			int raises = random.nextInt(4);
			int lastRaiseYear = EmployeeSeedGenerator.REFERENCE_DATE.getYear();
			int firstRaiseYear = Math.max(employee.hireDate().getYear() + 1, lastRaiseYear - raises + 1);
			for (int year = firstRaiseYear; year <= lastRaiseYear; year++) {
				amount = raise(amount, 2 + random.nextInt(9), currency);
				salaries.add(new SeedSalary(employee.employeeCode(), money(amount), currency, LocalDate.of(year, 1, 1)));
			}

			if (employee.employmentStatus() != EmploymentStatus.TERMINATED
					&& random.nextInt(100) < SCHEDULED_RAISE_PERCENT) {
				amount = raise(amount, 3 + random.nextInt(6), currency);
				salaries.add(new SeedSalary(employee.employeeCode(), money(amount), currency, SCHEDULED_RAISE_DATE));
			}
		}
		return salaries;
	}

	private static long raise(long amount, int percent, String currency) {
		return roundTo(amount * (100 + percent) / 100, currency);
	}

	// Round half up to a realistic granularity: thousands of rupees, hundreds elsewhere.
	// Integer arithmetic only; money never goes through floating point.
	private static long roundTo(long amount, String currency) {
		long unit = currency.equals("INR") ? 1_000 : 100;
		return (amount + unit / 2) / unit * unit;
	}

	private static BigDecimal money(long amount) {
		return BigDecimal.valueOf(amount).setScale(2);
	}

}
